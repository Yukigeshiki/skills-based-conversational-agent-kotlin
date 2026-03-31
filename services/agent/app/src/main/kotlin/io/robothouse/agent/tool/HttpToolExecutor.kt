package io.robothouse.agent.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.failsafe.FailsafeExecutor
import dev.failsafe.function.CheckedSupplier
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.model.HttpMethod
import io.robothouse.agent.util.log
import io.robothouse.agent.validator.SafeUrlValidator
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Result of an HTTP tool execution with the actual status code,
 * response body, and truncation flag.
 */
data class HttpToolExecutionResult(
    val statusCode: Int,
    val body: String,
    val truncated: Boolean
)

/**
 * Executes an HTTP tool by making an HTTP request to a configured endpoint
 * with the arguments provided by the LLM.
 *
 * Supports GET/DELETE (arguments as query params) and POST/PUT/PATCH
 * (arguments as JSON body). Resolves `{{ENV_VAR}}` placeholders in
 * header values from environment variables. HTTP calls are protected
 * by retry, circuit breaker, rate limiter, and bulkhead policies via
 * the injected [FailsafeExecutor].
 *
 * SSRF protection is applied at two points: at tool creation time via
 * the [@SafeUrl][io.robothouse.agent.validator.SafeUrl] validator, and
 * at runtime before each call via [SafeUrlValidator.isSafeUrl]. The
 * original URL is preserved for the HTTP request to ensure TLS/SNI
 * and virtual host routing work correctly.
 */
class HttpToolExecutor(
    private val endpointUrl: String,
    private val httpMethod: HttpMethod,
    private val headers: Map<String, String>,
    private val timeoutSeconds: Int,
    private val maxResponseLength: Int,
    private val httpClient: HttpClient,
    private val failsafe: FailsafeExecutor<Any>
) : ToolExecutor {

    companion object {
        private val objectMapper = jacksonObjectMapper()
        private val ENV_VAR_PATTERN = Regex("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}")
    }

    /**
     * Executes the HTTP tool call by parsing the LLM-provided arguments,
     * resolving header placeholders, performing a runtime SSRF check,
     * and making the configured HTTP request with Failsafe resilience.
     */
    override fun execute(request: ToolExecutionRequest, memoryId: Any?): String {
        return try {
            val result = executeWithDetails(request)
            result.body
        } catch (e: Exception) {
            log.warn { "HTTP tool call failed: ${e.message}" }
            "Error executing HTTP tool: ${e.message}"
        }
    }

    /**
     * Executes the HTTP call and returns a detailed result including the
     * actual HTTP status code, response body, and truncation flag.
     *
     * Performs a runtime SSRF validation before each call as a defence-in-depth
     * measure alongside the creation-time validation.
     */
    fun executeWithDetails(request: ToolExecutionRequest): HttpToolExecutionResult {
        if (!SafeUrlValidator.isSafeUrl(endpointUrl)) {
            return HttpToolExecutionResult(statusCode = 0, body = "Error: Endpoint URL is not allowed (private network or invalid scheme)", truncated = false)
        }

        val arguments: Map<String, Any> = if (request.arguments().isNullOrBlank() || request.arguments() == "{}") {
            emptyMap()
        } else {
            objectMapper.readValue(request.arguments())
        }

        val resolvedHeaders = headers.mapValues { (_, v) -> resolveEnvVars(v) }
        val httpRequest = buildHttpRequest(arguments, resolvedHeaders)

        val response = failsafe.get(CheckedSupplier {
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        }) as HttpResponse<String>

        val body = response.body() ?: ""
        val truncated = body.length > maxResponseLength
        val trimmedBody = if (truncated) {
            body.take(maxResponseLength) + "\n[Response truncated at $maxResponseLength characters]"
        } else {
            body
        }

        return HttpToolExecutionResult(
            statusCode = response.statusCode(),
            body = trimmedBody,
            truncated = truncated
        )
    }

    /**
     * Constructs the [HttpRequest] from the parsed arguments and resolved
     * headers. GET/DELETE methods pass arguments as query parameters;
     * POST/PUT/PATCH methods pass them as a JSON body.
     */
    private fun buildHttpRequest(
        arguments: Map<String, Any>,
        resolvedHeaders: Map<String, String>
    ): HttpRequest {
        val builder = when (httpMethod) {
            HttpMethod.GET,
            HttpMethod.DELETE -> {
                val url = buildUrlWithQueryParams(endpointUrl, arguments)
                HttpRequest.newBuilder(URI.create(url))
                    .method(httpMethod.value, HttpRequest.BodyPublishers.noBody())
            }
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH -> {
                val jsonBody = objectMapper.writeValueAsString(arguments)
                HttpRequest.newBuilder(URI.create(endpointUrl))
                    .method(httpMethod.value, HttpRequest.BodyPublishers.ofString(jsonBody))
            }
        }

        // Apply user headers
        resolvedHeaders.forEach { (name, value) -> builder.header(name, value) }

        // Set Content-Type for body-bearing methods only if not already provided by user
        if (httpMethod in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
            if (resolvedHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                builder.header("Content-Type", "application/json")
            }
        }

        builder.timeout(Duration.ofSeconds(timeoutSeconds.toLong()))

        return builder.build()
    }

    /**
     * Appends the given parameters as URL-encoded query string pairs to the
     * base URL, using `?` or `&` as appropriate.
     */
    private fun buildUrlWithQueryParams(baseUrl: String, params: Map<String, Any>): String {
        if (params.isEmpty()) return baseUrl
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v.toString(), StandardCharsets.UTF_8)}"
        }
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl$separator$queryString"
    }

    /**
     * Replaces `{{ENV_VAR}}` placeholders in the given string with their
     * corresponding environment variable values. Unresolved placeholders
     * are left as-is.
     */
    private fun resolveEnvVars(value: String): String {
        return ENV_VAR_PATTERN.replace(value) { match ->
            val envVar = match.groupValues[1]
            System.getenv(envVar) ?: match.value
        }
    }
}
