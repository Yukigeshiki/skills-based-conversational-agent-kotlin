package io.robothouse.agent.tool

import dev.failsafe.FailsafeExecutor
import dev.failsafe.function.CheckedSupplier
import dev.langchain4j.agent.tool.ToolExecutionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HttpToolExecutorTest {

    private val httpClient: HttpClient = mock()
    private val failsafe: FailsafeExecutor<Any> = mock()
    private val httpResponse: HttpResponse<String> = mock()

    @BeforeEach
    fun setUp() {
        whenever(failsafe.get(any<CheckedSupplier<Any>>())).thenAnswer { invocation ->
            val supplier = invocation.getArgument<CheckedSupplier<Any>>(0)
            supplier.get()
        }
    }

    private fun createExecutor(
        endpointUrl: String = "https://example.com/data",
        httpMethod: io.robothouse.agent.model.HttpMethod = io.robothouse.agent.model.HttpMethod.GET,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 30,
        maxResponseLength: Int = 8000
    ): HttpToolExecutor {
        return HttpToolExecutor(
            endpointUrl = endpointUrl,
            httpMethod = httpMethod,
            headers = headers,
            timeoutSeconds = timeoutSeconds,
            maxResponseLength = maxResponseLength,
            httpClient = httpClient,
            failsafe = failsafe
        )
    }

    private fun createRequest(arguments: String = """{"city": "London"}"""): ToolExecutionRequest {
        return ToolExecutionRequest.builder()
            .name("testTool")
            .arguments(arguments)
            .build()
    }

    @Test
    fun `returns response body on successful GET`() {
        whenever(httpResponse.body()).thenReturn("sunny weather")
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)

        val executor = createExecutor()
        val result = executor.execute(createRequest(), null)

        assertEquals("sunny weather", result)
    }

    @Test
    fun `returns response body on successful POST`() {
        whenever(httpResponse.body()).thenReturn("created")
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)

        val executor = createExecutor(httpMethod = io.robothouse.agent.model.HttpMethod.POST)
        val result = executor.execute(createRequest(), null)

        assertEquals("created", result)
        verify(httpClient).send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())
    }

    @Test
    fun `truncates response exceeding maxResponseLength`() {
        whenever(httpResponse.body()).thenReturn("a]long response body here")
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)

        val executor = createExecutor(maxResponseLength = 10)
        val result = executor.execute(createRequest(), null)

        assertTrue(result.startsWith("a]long res"))
        assertTrue(result.contains("[Response truncated at 10 characters]"))
    }

    @Test
    fun `returns full response when within maxResponseLength`() {
        whenever(httpResponse.body()).thenReturn("short")
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)

        val executor = createExecutor(maxResponseLength = 100)
        val result = executor.execute(createRequest(), null)

        assertEquals("short", result)
    }

    @Test
    fun `returns error string on HTTP exception`() {
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()))
            .thenThrow(IOException("Connection refused"))

        val executor = createExecutor()
        val result = executor.execute(createRequest(), null)

        assertTrue(result.contains("Error executing HTTP tool"))
        assertTrue(result.contains("Connection refused"))
    }

    @Test
    fun `returns error for unsafe endpoint URL`() {
        val executor = createExecutor(endpointUrl = "http://localhost:8080")
        val result = executor.execute(createRequest(), null)

        assertEquals("Error: Endpoint URL is not allowed (private network or invalid scheme)", result)
    }

    @Test
    fun `handles empty arguments`() {
        whenever(httpResponse.body()).thenReturn("ok")
        whenever(httpClient.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>())).thenReturn(httpResponse)

        val executor = createExecutor()
        val result = executor.execute(createRequest(arguments = "{}"), null)

        assertEquals("ok", result)
    }
}
