package io.robothouse.agent.service

import dev.failsafe.FailsafeExecutor
import dev.failsafe.function.CheckedSupplier
import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.exception.ConflictException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.HttpMethod
import io.robothouse.agent.model.HttpToolParameter
import io.robothouse.agent.model.ParameterType
import io.robothouse.agent.repository.HttpToolRepository
import io.robothouse.agent.repository.ToolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.UUID

class HttpToolServiceTest {

    private val httpToolRepository: HttpToolRepository = mock()
    private val httpToolCacheService: HttpToolCacheService = mock()
    private val transactionTemplate: TransactionTemplate = mock()
    private val toolRepository: ToolRepository = mock()
    private lateinit var service: HttpToolService

    private val toolId = UUID.randomUUID()
    private val tool = HttpTool(
        id = toolId,
        name = "weatherApi",
        description = "Gets weather data",
        endpointUrl = "https://example.com/weather",
        httpMethod = HttpMethod.GET,
        headers = mapOf("Authorization" to "Bearer test"),
        parameters = listOf(HttpToolParameter(name = "city", type = ParameterType.STRING, description = "City name", required = true)),
        timeoutSeconds = 30,
        maxResponseLength = 8000
    )

    @BeforeEach
    fun setUp() {
        service = HttpToolService(httpToolRepository, httpToolCacheService, transactionTemplate, toolRepository)
        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<org.springframework.transaction.support.TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
        whenever(toolRepository.getBeanToolNames()).thenReturn(setOf("DateTimeTool"))
    }

    @Test
    fun `findAll returns all HTTP tools`() {
        whenever(httpToolRepository.findAll()).thenReturn(listOf(tool))
        val result = service.findAll()
        assertEquals(1, result.size)
        assertEquals("weatherApi", result[0].name)
    }

    @Test
    fun `findById returns tool when exists`() {
        whenever(httpToolRepository.findById(toolId)).thenReturn(Optional.of(tool))
        val result = service.findById(toolId)
        assertEquals("weatherApi", result.name)
    }

    @Test
    fun `findById throws NotFoundException when not found`() {
        whenever(httpToolRepository.findById(toolId)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { service.findById(toolId) }
    }

    @Test
    fun `create persists tool and returns saved entity`() {
        whenever(httpToolRepository.findByName("weatherApi")).thenReturn(null)
        whenever(httpToolRepository.save(any<HttpTool>())).thenReturn(tool)
        val request = io.robothouse.agent.model.CreateHttpToolRequest(
            name = "weatherApi",
            description = "Gets weather data",
            endpointUrl = "https://example.com/weather",
            httpMethod = HttpMethod.GET,
            headers = emptyMap(),
            parameters = emptyList(),
            timeoutSeconds = 30,
            maxResponseLength = 8000
        )
        val result = service.create(request)
        assertNotNull(result)
        assertEquals("weatherApi", result.name)
    }

    @Test
    fun `create throws ConflictException when name exists`() {
        whenever(httpToolRepository.findByName("weatherApi")).thenReturn(tool)
        val request = io.robothouse.agent.model.CreateHttpToolRequest(
            name = "weatherApi",
            description = "Gets weather data",
            endpointUrl = "https://example.com/weather",
            httpMethod = HttpMethod.GET,
            headers = emptyMap(),
            parameters = emptyList(),
            timeoutSeconds = 30,
            maxResponseLength = 8000
        )
        assertThrows<ConflictException> { service.create(request) }
    }

    @Test
    fun `create throws ConflictException when name conflicts with bean tool`() {
        val request = io.robothouse.agent.model.CreateHttpToolRequest(
            name = "DateTimeTool",
            description = "Conflicts",
            endpointUrl = "https://example.com",
            httpMethod = HttpMethod.GET,
            headers = emptyMap(),
            parameters = emptyList(),
            timeoutSeconds = 30,
            maxResponseLength = 8000
        )
        assertThrows<ConflictException> { service.create(request) }
    }

    @Test
    fun `update calls patchUpdate and returns updated entity`() {
        whenever(httpToolRepository.patchUpdate(any(), any())).thenReturn(tool)
        val request = io.robothouse.agent.model.UpdateHttpToolRequest(description = "Updated")
        val result = service.update(toolId, request)
        assertEquals("weatherApi", result.name)
    }

    @Test
    fun `update throws NotFoundException when tool not found`() {
        whenever(httpToolRepository.patchUpdate(any(), any())).thenReturn(null)
        val request = io.robothouse.agent.model.UpdateHttpToolRequest(description = "Updated")
        assertThrows<NotFoundException> { service.update(toolId, request) }
    }

    @Test
    fun `delete removes tool by ID`() {
        whenever(httpToolRepository.findById(toolId)).thenReturn(Optional.of(tool))
        service.delete(toolId)
    }

    @Test
    fun `delete throws NotFoundException when not found`() {
        whenever(httpToolRepository.findById(toolId)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { service.delete(toolId) }
    }

    @Test
    fun `getHttpToolNames returns tool names from cache`() {
        val cached = mapOf("weatherApi" to HttpToolCacheService.CachedTool(mock(), mock()))
        whenever(httpToolCacheService.getAll()).thenReturn(cached)
        val result = service.getHttpToolNames()
        assertEquals(setOf("weatherApi"), result)
    }

    @Test
    fun `getSpecificationsByNames returns specs for known names`() {
        val spec: dev.langchain4j.agent.tool.ToolSpecification = mock()
        val cached = mapOf("weatherApi" to HttpToolCacheService.CachedTool(spec, mock()))
        whenever(httpToolCacheService.getAll()).thenReturn(cached)
        val result = service.getSpecificationsByNames(listOf("weatherApi"))
        assertEquals(1, result.size)
    }

    @Test
    fun `getExecutorsByNames returns executors for known names`() {
        val executor: dev.langchain4j.service.tool.ToolExecutor = mock()
        val cached = mapOf("weatherApi" to HttpToolCacheService.CachedTool(mock(), executor))
        whenever(httpToolCacheService.getAll()).thenReturn(cached)
        val result = service.getExecutorsByNames(listOf("weatherApi"))
        assertEquals(1, result.size)
    }
}
