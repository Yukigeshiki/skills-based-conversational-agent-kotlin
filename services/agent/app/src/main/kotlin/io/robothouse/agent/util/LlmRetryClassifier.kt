package io.robothouse.agent.util

import java.net.BindException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException

/**
 * Classifies whether a throwable from an LLM call should be retried. Walks the
 * cause chain to a fixed depth, treats specific transient transport errors
 * (`SocketException`, `SocketTimeoutException`, `UnknownHostException`,
 * `HttpTimeoutException`) as retryable, reflectively probes LangChain4j HTTP
 * exceptions for retryable status codes (avoids a hard SDK dependency), and
 * falls back to keyword matching when the SDK swallows the original type.
 * Broader `IOException` subclasses (`MalformedURLException`, etc.) and generic
 * [RuntimeException] without retryable indicators are NOT retried — they
 * indicate misconfiguration or application bugs that should fail fast.
 */
object LlmRetryClassifier {

    private val RETRYABLE_STATUS_CODES = setOf(408, 409, 429, 500, 502, 503, 504, 529)
    private val RETRYABLE_KEYWORDS = listOf(
        "rate limit",
        "overloaded",
        "503",
        "504",
        "529",
        "timeout",
        "timed out",
        "connection reset"
    )
    private const val MAX_CAUSE_DEPTH = 5

    /** Returns true when the throwable (or any cause within depth) is a known transient error. */
    fun isRetryable(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (matches(current)) return true
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Returns a short, sanitised description of [throwable] suitable for showing
     * to end users (e.g. in retry-event UI). Walks the cause chain and maps the
     * first recognised category to a generic label like "Rate limited" or
     * "Service unavailable" — the raw `throwable.message` is intentionally never
     * surfaced because it can contain provider response bodies, transport
     * details, or other internal payloads. Falls back to "Transient error" when
     * nothing matches.
     */
    fun safeDescription(throwable: Throwable): String {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            categorise(current)?.let { return it }
            current = current.cause
            depth++
        }
        return "Transient error"
    }

    /**
     * Returns true when [t] is a known transient error. Checks specific transport
     * exception types first, then falls back to reflective HTTP status code probing
     * for SDK-thrown exceptions, and finally to keyword matching on the error message.
     * `IOException` as a broad catch is intentionally avoided because it includes
     * non-transient subclasses like `MalformedURLException`, `FileNotFoundException`,
     * and `EOFException`.
     */
    private fun matches(t: Throwable): Boolean {
        // Carve out SocketException subclasses that indicate local config / routing
        // failures rather than transient provider issues. Retrying these only burns
        // through the backoff budget with no chance of recovery.
        if (t is BindException || t is NoRouteToHostException || t is PortUnreachableException) return false

        if (t is SocketException) return true        // ConnectException + bare "connection reset" / "broken pipe"
        if (t is SocketTimeoutException) return true // separate hierarchy under InterruptedIOException
        if (t is UnknownHostException) return true   // DNS failures
        if (t is HttpTimeoutException) return true

        val className = t::class.java.name
        if (className.endsWith("HttpException") || className.endsWith("HttpStatusException")) {
            val statusCode = extractStatusCode(t)
            if (statusCode != null && statusCode in RETRYABLE_STATUS_CODES) return true
        }

        val message = t.message?.lowercase().orEmpty()
        return message.isNotBlank() && RETRYABLE_KEYWORDS.any { message.contains(it) }
    }

    /**
     * Returns a sanitised category label for [t] when it matches a known transient
     * pattern, or null otherwise. Mirrors the structure of [matches] so the two
     * stay in sync. Status codes are public HTTP semantics — safe to surface — but
     * `t.message` is never returned, since it can contain provider response bodies.
     */
    private fun categorise(t: Throwable): String? {
        if (t is BindException || t is NoRouteToHostException || t is PortUnreachableException) return null

        if (t is SocketTimeoutException || t is HttpTimeoutException) return "Request timed out"
        if (t is UnknownHostException) return "DNS resolution failed"
        if (t is SocketException) return "Network connection issue"

        val className = t::class.java.name
        if (className.endsWith("HttpException") || className.endsWith("HttpStatusException")) {
            val statusCode = extractStatusCode(t)
            if (statusCode != null && statusCode in RETRYABLE_STATUS_CODES) {
                return statusLabel(statusCode)
            }
        }

        val message = t.message?.lowercase().orEmpty()
        if (message.isBlank()) return null
        return when {
            message.contains("rate limit") -> "Rate limited"
            message.contains("overloaded") || message.contains("529") -> "Provider overloaded"
            message.contains("503") -> "Service unavailable"
            message.contains("504") -> "Gateway timeout"
            message.contains("timeout") || message.contains("timed out") -> "Request timed out"
            message.contains("connection reset") -> "Network connection issue"
            else -> null
        }
    }

    private fun statusLabel(statusCode: Int): String = when (statusCode) {
        408 -> "Request timed out (HTTP 408)"
        409 -> "Conflict (HTTP 409)"
        429 -> "Rate limited (HTTP 429)"
        500 -> "Provider error (HTTP 500)"
        502 -> "Bad gateway (HTTP 502)"
        503 -> "Service unavailable (HTTP 503)"
        504 -> "Gateway timeout (HTTP 504)"
        529 -> "Provider overloaded (HTTP 529)"
        else -> "Provider error (HTTP $statusCode)"
    }

    /**
     * Reflectively probes [t] for an HTTP status code via a no-arg `statusCode()`
     * or `getStatusCode()` method. Returns null when no such method exists, when
     * the method returns a non-numeric value, or when the reflective call fails
     * for any reason. Used to detect LangChain4j HTTP exceptions defensively
     * without compile-time coupling to a specific SDK class.
     */
    private fun extractStatusCode(t: Throwable): Int? {
        return runCatching {
            val method = t::class.java.methods.firstOrNull {
                it.parameterCount == 0 && (it.name == "statusCode" || it.name == "getStatusCode")
            } ?: return@runCatching null
            when (val result = method.invoke(t)) {
                is Int -> result
                is Number -> result.toInt()
                else -> null
            }
        }
            .onFailure { ex ->
                log.debug { "Failed to extract status code from ${t::class.java.simpleName}: ${ex.message}" }
            }
            .getOrNull()
    }
}
