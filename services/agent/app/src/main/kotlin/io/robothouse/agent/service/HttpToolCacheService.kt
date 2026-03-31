package io.robothouse.agent.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.model.ParameterType
import io.robothouse.agent.repository.HttpToolRepository
import io.robothouse.agent.tool.HttpToolExecutor
import io.robothouse.agent.util.log
import dev.failsafe.FailsafeExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.net.http.HttpClient
import java.time.Duration

/**
 * Short-lived cache for HTTP tool specifications and executors, preventing
 * repeated database queries and object construction during the same chat
 * request. The cache has a short TTL so that tool changes are reflected
 * quickly without relying solely on explicit invalidation.
 */
@Service
class HttpToolCacheService(
    private val httpToolRepository: HttpToolRepository,
    @param:Qualifier("httpToolHttpClient") private val httpClient: HttpClient,
    @param:Qualifier("httpToolFailsafe") private val failsafe: FailsafeExecutor<Any>
) {

    companion object {
        private const val CACHE_KEY = "all_http_tools"
        private const val CACHE_TTL_SECONDS = 10L
    }

    /**
     * Holds the built specification and executor for a single HTTP tool.
     */
    data class CachedTool(
        val specification: ToolSpecification,
        val executor: ToolExecutor
    )

    private val toolsCache: Cache<String, Map<String, CachedTool>> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
        .maximumSize(1)
        .build()

    /**
     * Returns all HTTP tools as a map of tool name to cached specification
     * and executor, using a short-lived cache to avoid redundant queries.
     */
    fun getAll(): Map<String, CachedTool> {
        return toolsCache.get(CACHE_KEY) {
            log.debug { "HTTP tool cache miss — loading from database" }
            val tools = httpToolRepository.findAll()
            tools.associate { tool ->
                tool.name to CachedTool(
                    specification = buildSpecification(tool),
                    executor = buildExecutor(tool)
                )
            }
        }
    }

    /**
     * Invalidates the cached tool map. Should be called after HTTP tool
     * create, update, or delete operations.
     */
    fun invalidate() {
        log.debug { "Invalidating HTTP tool cache" }
        toolsCache.invalidateAll()
    }

    /**
     * Builds a LangChain4j [ToolSpecification] from an [HttpTool] entity,
     * mapping each parameter definition to the appropriate JSON schema
     * property type.
     */
    private fun buildSpecification(tool: HttpTool): ToolSpecification {
        val schemaBuilder = JsonObjectSchema.builder()
        val requiredParams = mutableListOf<String>()

        tool.parameters.forEach { param ->
            when (param.type) {
                ParameterType.STRING -> schemaBuilder.addStringProperty(param.name, param.description)
                ParameterType.INTEGER -> schemaBuilder.addIntegerProperty(param.name, param.description)
                ParameterType.NUMBER -> schemaBuilder.addNumberProperty(param.name, param.description)
                ParameterType.BOOLEAN -> schemaBuilder.addBooleanProperty(param.name, param.description)
            }
            if (param.required) requiredParams.add(param.name)
        }

        if (requiredParams.isNotEmpty()) {
            schemaBuilder.required(requiredParams)
        }

        return ToolSpecification.builder()
            .name(tool.name)
            .description(tool.description)
            .parameters(schemaBuilder.build())
            .build()
    }

    /**
     * Creates an [HttpToolExecutor] configured from the given [HttpTool]
     * entity.
     */
    private fun buildExecutor(tool: HttpTool): HttpToolExecutor {
        return HttpToolExecutor(
            endpointUrl = tool.endpointUrl,
            httpMethod = tool.httpMethod,
            headers = tool.headers,
            timeoutSeconds = tool.timeoutSeconds,
            maxResponseLength = tool.maxResponseLength,
            httpClient = httpClient,
            failsafe = failsafe
        )
    }
}
