package io.robothouse.agent.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.exception.ConflictException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.CreateHttpToolRequest
import io.robothouse.agent.model.HttpToolParameter
import io.robothouse.agent.model.TestHttpToolRequest
import io.robothouse.agent.model.TestHttpToolResponse
import io.robothouse.agent.model.UpdateHttpToolRequest
import io.robothouse.agent.repository.HttpToolRepository
import io.robothouse.agent.repository.ToolRepository
import io.robothouse.agent.util.log
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Service layer for HTTP tool CRUD operations, test execution, and
 * tool discovery.
 *
 * Delegates tool specification and executor caching to [HttpToolCacheService].
 * The [io.robothouse.agent.repository.ToolRepository] delegates to this
 * service for HTTP tool lookups.
 */
@Service
class HttpToolService(
    private val httpToolRepository: HttpToolRepository,
    private val httpToolCacheService: HttpToolCacheService,
    private val transactionTemplate: TransactionTemplate,
    @param:Lazy private val toolRepository: ToolRepository
) {

    private val objectMapper = jacksonObjectMapper()

    /**
     * Returns the names of all registered HTTP tools.
     */
    fun getHttpToolNames(): Set<String> = httpToolCacheService.getAll().keys

    /**
     * Returns tool specifications for the given HTTP tool names.
     */
    fun getSpecificationsByNames(names: List<String>): List<ToolSpecification> {
        val cached = httpToolCacheService.getAll()
        return names.mapNotNull { cached[it]?.specification }
    }

    /**
     * Returns a map of tool names to their executors for the given HTTP
     * tool names.
     */
    fun getExecutorsByNames(names: List<String>): Map<String, ToolExecutor> {
        val cached = httpToolCacheService.getAll()
        return names.mapNotNull { name -> cached[name]?.let { name to it.executor } }.toMap()
    }

    /**
     * Returns all HTTP tools.
     */
    fun findAll(): List<HttpTool> {
        log.debug { "Fetching all HTTP tools" }
        return httpToolRepository.findAll()
    }

    /**
     * Returns the HTTP tool with the given ID.
     */
    fun findById(id: UUID): HttpTool {
        log.debug { "Retrieving HTTP tool: id=$id" }
        return httpToolRepository.findById(id).orElseThrow {
            log.warn { "Attempt to access non-existent HTTP tool: id=$id" }
            NotFoundException("HTTP tool not found")
        }
    }

    /**
     * Creates a new HTTP tool after verifying the name is available.
     */
    fun create(request: CreateHttpToolRequest): HttpTool {
        log.debug { "Processing create request for HTTP tool: name=${request.name}" }

        val saved = transactionTemplate.execute {
            if (request.name in toolRepository.getBeanToolNames()) {
                throw ConflictException("Name '${request.name}' conflicts with a built-in tool")
            }
            if (httpToolRepository.findByName(request.name) != null) {
                throw ConflictException("An HTTP tool with name '${request.name}' already exists")
            }

            val tool = HttpTool(
                name = request.name,
                description = request.description,
                endpointUrl = request.endpointUrl,
                httpMethod = request.httpMethod,
                headers = request.headers,
                parameters = request.parameters.map {
                    HttpToolParameter(name = it.name, type = it.type, description = it.description, required = it.required)
                },
                timeoutSeconds = request.timeoutSeconds,
                maxResponseLength = request.maxResponseLength
            )

            val persisted = httpToolRepository.save(tool)
            httpToolCacheService.invalidate()
            persisted
        }!!

        log.info { "Created HTTP tool: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Partially updates the HTTP tool with the given ID, applying only
     * non-null fields.
     */
    fun update(id: UUID, request: UpdateHttpToolRequest): HttpTool {
        log.debug { "Processing update request for HTTP tool: id=$id" }

        val saved = transactionTemplate.execute {
            request.name?.let { newName ->
                if (newName in toolRepository.getBeanToolNames()) {
                    throw ConflictException("Name '$newName' conflicts with a built-in tool")
                }
                val existing = httpToolRepository.findByName(newName)
                if (existing != null && existing.id != id) {
                    throw ConflictException("An HTTP tool with name '$newName' already exists")
                }
            }

            val persisted = httpToolRepository.patchUpdate(id, request)
                ?: run {
                    log.warn { "Attempt to update non-existent HTTP tool: id=$id" }
                    throw NotFoundException("HTTP tool not found")
                }

            httpToolCacheService.invalidate()
            persisted
        }!!

        log.info { "Updated HTTP tool: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Deletes the HTTP tool with the given ID.
     */
    fun delete(id: UUID) {
        log.debug { "Processing delete request for HTTP tool: id=$id" }

        transactionTemplate.execute {
            httpToolRepository.findById(id).orElseThrow {
                log.warn { "Attempt to delete non-existent HTTP tool: id=$id" }
                NotFoundException("HTTP tool not found")
            }

            httpToolRepository.deleteById(id)
            httpToolCacheService.invalidate()
        }

        log.info { "Deleted HTTP tool: id=$id" }
    }

    /**
     * Executes the HTTP tool's endpoint with the provided sample arguments
     * and returns the result.
     */
    fun testTool(id: UUID, request: TestHttpToolRequest): TestHttpToolResponse {
        log.debug { "Processing test request for HTTP tool: id=$id" }

        val tool = findById(id)

        // Get executor from cache if available, otherwise the cache will build one
        val cached = httpToolCacheService.getAll()[tool.name]
        val executor = cached?.executor as? io.robothouse.agent.tool.HttpToolExecutor
            ?: throw NotFoundException("HTTP tool executor not found for: ${tool.name}")

        val argsJson = objectMapper.writeValueAsString(request.arguments)
        val toolRequest = ToolExecutionRequest.builder()
            .name(tool.name)
            .arguments(argsJson)
            .build()

        val start = System.currentTimeMillis()
        val result = executor.executeWithDetails(toolRequest)
        val durationMs = System.currentTimeMillis() - start

        return TestHttpToolResponse(
            statusCode = result.statusCode,
            body = result.body,
            durationMs = durationMs,
            truncated = result.truncated
        )
    }
}
