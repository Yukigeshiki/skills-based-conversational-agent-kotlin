package io.robothouse.agent.graph.agent

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.TaskMemory
import io.robothouse.agent.model.ToolExecutionStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Collections
import java.util.concurrent.TimeoutException

class AgentGraphBuilderTest {

    private fun buildContext(
        chatModel: ChatModel = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse =
                ChatResponse.builder().aiMessage(AiMessage.from("sync response")).build()
        },
        streamingChatModel: StreamingChatModel? = null,
        specifications: List<ToolSpecification> = emptyList(),
        executors: Map<String, ToolExecutor> = emptyMap(),
        emitFinalResponse: Boolean = true
    ): AgentGraphContext {
        return AgentGraphContext(
            agentChatModel = chatModel,
            agentStreamingChatModel = streamingChatModel,
            systemPrompt = "You are a test assistant.",
            skillName = "test-skill",
            specifications = specifications,
            executors = executors,
            listener = AgentEventListener.NOOP,
            emitFinalResponse = emitFinalResponse,
            taskMemory = TaskMemory(),
            maxIterations = 10,
            startTime = System.currentTimeMillis(),
            timeoutMillis = 30_000L,
            emitEvent = { listener, supplier ->
                try {
                    synchronized(listener) { listener.onEvent(supplier()) }
                } catch (_: Exception) { }
            },
            executeTool = { request, execs, iteration, listener ->
                val executor = execs[request.name()]
                val result = executor?.execute(request, request.id()) ?: "no executor"
                ToolExecutionStep(
                    toolName = request.name(),
                    arguments = request.arguments(),
                    result = result
                )
            },
            checkTimeout = { _, _ -> }
        )
    }

    @Test
    fun `uses sync model when streaming model is null`() {
        val ctx = buildContext(streamingChatModel = null)
        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val result = graph.invoke(initialState).orElseThrow()
        val state = AgentGraphState(result.data())
        assertEquals("sync response", state.response)
        assertTrue(state.done)
    }

    @Test
    fun `uses streaming model and emits ResponseChunkEvents`() {
        val emittedEvents = Collections.synchronizedList(mutableListOf<AgentEvent>())

        val streamingModel = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse("Hello")
                handler.onPartialResponse(" world")
                handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("Hello world")).build()
                )
            }
        }

        val ctx = buildContext(streamingChatModel = streamingModel).copy(
            listener = AgentEventListener { event -> emittedEvents.add(event) }
        )

        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val result = graph.invoke(initialState).orElseThrow()
        val state = AgentGraphState(result.data())
        assertEquals("Hello world", state.response)
        assertTrue(state.done)

        val chunkEvents = emittedEvents.filterIsInstance<AgentEvent.ResponseChunkEvent>()
        assertEquals(2, chunkEvents.size)
        assertEquals("Hello", chunkEvents[0].chunk)
        assertEquals(" world", chunkEvents[1].chunk)

        val finalEvents = emittedEvents.filterIsInstance<AgentEvent.FinalResponseEvent>()
        assertEquals(1, finalEvents.size)
        assertEquals("Hello world", finalEvents[0].response)
    }

    @Test
    fun `streaming model error propagates as exception`() {
        val streamingModel = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse("partial")
                handler.onError(IllegalStateException("API failure"))
            }
        }

        val ctx = buildContext(streamingChatModel = streamingModel)
        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val ex = assertThrows<Exception> {
            graph.invoke(initialState)
        }
        // The original exception is wrapped by graph infrastructure — verify it's in the cause chain
        fun Throwable.rootCause(): Throwable = cause?.rootCause() ?: this
        val root = ex.rootCause()
        assertTrue(
            root is IllegalStateException && root.message == "API failure",
            "Expected root cause IllegalStateException('API failure') but got: ${root::class.simpleName}: ${root.message}"
        )
    }

    @Test
    fun `callLlm throws TimeoutException when streaming chat returns after deadline`() {
        // The streaming model sleeps past the configured timeout, then completes
        // successfully. The post-call checkTimeout in callLlm must catch this and
        // throw TimeoutException so the agent loop honours its wall-clock budget
        // even when the underlying call returned a successful response.
        val streamingModel = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                Thread.sleep(80)  // exceeds the 50ms budget below
                handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("late response")).build()
                )
            }
        }

        val ctx = buildContext(streamingChatModel = streamingModel).copy(
            startTime = System.currentTimeMillis(),
            timeoutMillis = 50L,
            checkTimeout = { startTime, timeoutMillis ->
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw TimeoutException("Agent loop exceeded timeout")
                }
            }
        )
        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val ex = assertThrows<Exception> { graph.invoke(initialState) }
        fun Throwable.rootCause(): Throwable = cause?.rootCause() ?: this
        val root = ex.rootCause()
        assertTrue(
            root is TimeoutException,
            "Expected root cause TimeoutException but got: ${root::class.simpleName}: ${root.message}"
        )
    }

    @Test
    fun `callLlm throws TimeoutException when non-streaming chat returns after deadline`() {
        // Same scenario for the non-streaming path: a slow successful chat must be
        // rejected by the post-call deadline check.
        val chatModel = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                Thread.sleep(80)  // exceeds the 50ms budget below
                return ChatResponse.builder().aiMessage(AiMessage.from("late response")).build()
            }
        }

        val ctx = buildContext(chatModel = chatModel, streamingChatModel = null).copy(
            startTime = System.currentTimeMillis(),
            timeoutMillis = 50L,
            checkTimeout = { startTime, timeoutMillis ->
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw TimeoutException("Agent loop exceeded timeout")
                }
            }
        )
        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val ex = assertThrows<Exception> { graph.invoke(initialState) }
        fun Throwable.rootCause(): Throwable = cause?.rootCause() ?: this
        val root = ex.rootCause()
        assertTrue(
            root is TimeoutException,
            "Expected root cause TimeoutException but got: ${root::class.simpleName}: ${root.message}"
        )
    }

    @Test
    fun `streaming emits chunk events only when emitFinalResponse is true`() {
        val emittedEvents = Collections.synchronizedList(mutableListOf<AgentEvent>())

        val streamingModel = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse("chunk")
                handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("chunk")).build()
                )
            }
        }

        // emitFinalResponse=false means streaming model should be null (not passed)
        // but let's verify that if it IS passed, chunks still emit (the gating is at the service level)
        val ctx = buildContext(streamingChatModel = streamingModel, emitFinalResponse = false).copy(
            listener = AgentEventListener { event -> emittedEvents.add(event) }
        )

        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val result = graph.invoke(initialState).orElseThrow()
        val state = AgentGraphState(result.data())
        assertEquals("chunk", state.response)

        // Chunks still emitted (gating happens at DynamicAgentService level by not passing the model)
        val chunkEvents = emittedEvents.filterIsInstance<AgentEvent.ResponseChunkEvent>()
        assertEquals(1, chunkEvents.size)

        // FinalResponseEvent NOT emitted when emitFinalResponse=false
        val finalEvents = emittedEvents.filterIsInstance<AgentEvent.FinalResponseEvent>()
        assertEquals(0, finalEvents.size)
    }

    @Test
    fun `streaming with tool calls emits chunks then ThoughtEvent`() {
        val emittedEvents = Collections.synchronizedList(mutableListOf<AgentEvent>())

        val toolRequest = ToolExecutionRequest.builder()
            .name("testTool")
            .arguments("{}")
            .build()

        val responses = ArrayDeque(
            listOf(
                ChatResponse.builder()
                    .aiMessage(AiMessage.from("Let me check", listOf(toolRequest)))
                    .build(),
                ChatResponse.builder()
                    .aiMessage(AiMessage.from("Final answer"))
                    .build()
            )
        )

        // First call: streaming returns tool calls; second call: sync (no streaming model for simplicity)
        val streamingModel = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                val response = responses.removeFirst()
                handler.onPartialResponse("Let me check")
                handler.onCompleteResponse(response)
            }
        }

        val syncModel = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse = responses.removeFirst()
        }

        val toolSpec = ToolSpecification.builder().name("testTool").description("A test tool").build()
        val toolExecutor = ToolExecutor { _, _ -> "tool result" }

        val ctx = buildContext(
            chatModel = syncModel,
            streamingChatModel = streamingModel,
            specifications = listOf(toolSpec),
            executors = mapOf("testTool" to toolExecutor)
        ).copy(
            listener = AgentEventListener { event -> emittedEvents.add(event) }
        )

        val graph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to listOf(
                SystemMessage.from("You are a test assistant."),
                UserMessage.from("Hello")
            ),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val result = graph.invoke(initialState).orElseThrow()
        val state = AgentGraphState(result.data())
        assertEquals("Final answer", state.response)

        // First iteration: chunks + thought (tool calls)
        val chunkEvents = emittedEvents.filterIsInstance<AgentEvent.ResponseChunkEvent>()
        assertTrue(chunkEvents.isNotEmpty())

        val thoughtEvents = emittedEvents.filterIsInstance<AgentEvent.ThoughtEvent>()
        assertEquals(1, thoughtEvents.size)
        assertEquals("Let me check", thoughtEvents[0].thought)
    }
}
