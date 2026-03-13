package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ConversationMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamingChatServiceTest {

    private val skillRouterService: SkillRouterService = mock()
    private val dynamicAgentService: DynamicAgentService = mock()
    private val conversationMemoryService: ConversationMemoryService = mock()
    private val responseValidationService: ResponseValidationService = mock()
    private val agentProperties = AgentProperties(maxIterations = 10, toolExecutionTimeoutSeconds = 30, maxPlanSteps = 10)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private lateinit var service: StreamingChatService

    private val skill = Skill(
        name = "test-skill",
        description = "test",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool")
    )

    @BeforeEach
    fun setUp() {
        whenever(responseValidationService.isAdequate(any(), any())).thenReturn(true)
        service = StreamingChatService(
            skillRouterService,
            dynamicAgentService,
            conversationMemoryService,
            responseValidationService,
            agentProperties,
            objectMapper
        )
    }

    @Test
    fun `returns SseEmitter with correct timeout`() {
        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi", null)

        // Timeout should be toolExecutionTimeoutSeconds * 1000 + 5000 = 35000
        assertNotNull(emitter.timeout)
        assertEquals(35000L, emitter.timeout)
    }

    @Test
    fun `generates conversation ID when none provided`() {
        val latch = CountDownLatch(1)

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenAnswer {
            AgentResponse(response = "Hello!")
        }

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        // Verify memory was called with a generated ID (any non-null string)
        verify(conversationMemoryService).getHistory(any())
        // addMessage called twice: once for user message, once for assistant message
        verify(conversationMemoryService, org.mockito.kotlin.times(2)).addMessage(any(), any())
    }

    @Test
    fun `uses provided conversation ID`() {
        val latch = CountDownLatch(1)

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(eq("my-conv-id"))).thenReturn(emptyList())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi", "my-conv-id")
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(conversationMemoryService).getHistory(eq("my-conv-id"))
    }

    @Test
    fun `stores user message before agent loop and assistant message after`() {
        val latch = CountDownLatch(1)
        val storedMessages = mutableListOf<ConversationMessage>()

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        doAnswer { invocation ->
            storedMessages.add(invocation.getArgument(1))
            Unit
        }.whenever(conversationMemoryService).addMessage(any(), any())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi there", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        assertEquals(2, storedMessages.size)
        assertEquals("user", storedMessages[0].role)
        assertEquals("Hi there", storedMessages[0].content)
        assertEquals("assistant", storedMessages[1].role)
        assertEquals("Hello!", storedMessages[1].content)
    }

    @Test
    fun `passes conversation history to agent service`() {
        val latch = CountDownLatch(1)
        val history = listOf(
            ConversationMessage(role = "user", content = "Previous message"),
            ConversationMessage(role = "assistant", content = "Previous response")
        )

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(history)
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "I remember!")
        )

        val emitter = service.streamChat("New message", "conv-id")
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(dynamicAgentService).chat(eq(skill), eq("New message"), any(), eq(history))
    }

    @Test
    fun `emits warning and continues with empty history when memory unavailable`() {
        val latch = CountDownLatch(1)

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenThrow(RuntimeException("Redis down"))
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        // Should pass empty history when memory fails
        verify(dynamicAgentService).chat(eq(skill), eq("Hi"), any(), eq(emptyList()))
    }

    @Test
    fun `emits warning when storing assistant response fails`() {
        val latch = CountDownLatch(1)
        var addMessageCallCount = 0

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        doAnswer {
            addMessageCallCount++
            if (addMessageCallCount == 2) throw RuntimeException("Redis down")
        }.whenever(conversationMemoryService).addMessage(any(), any())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        // Service should complete without throwing
        assertEquals(2, addMessageCallCount)
    }

    @Test
    fun `routes to correct skill`() {
        val latch = CountDownLatch(1)

        whenever(skillRouterService.route(eq("What time is it?"), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "It's 3pm")
        )

        val emitter = service.streamChat("What time is it?", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(skillRouterService).route(eq("What time is it?"), any())
        verify(dynamicAgentService).chat(eq(skill), eq("What time is it?"), any(), any())
    }

    @Test
    fun `completes emitter with error when agent loop throws`() {
        val latch = CountDownLatch(1)

        whenever(skillRouterService.route(any(), any())).thenThrow(RuntimeException("Routing failed"))
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }
        emitter.onError { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        // Should not throw — error is sent via SSE
    }

    @Test
    fun `passes conversation history to skill router`() {
        val latch = CountDownLatch(1)
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM in Tokyo.")
        )

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(history)
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Sure!")
        )

        val emitter = service.streamChat("yes", "conv-id")
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(skillRouterService).route(eq("yes"), eq(history))
    }

    @Test
    fun `reroutes to fallback when specialist response is inadequate`() {
        val latch = CountDownLatch(1)
        val fallbackSkill = Skill(
            name = "general-assistant",
            description = "general",
            systemPrompt = "You are a general assistant.",
            toolNames = emptyList()
        )

        whenever(skillRouterService.route(any(), any())).thenReturn(skill)
        whenever(skillRouterService.findFallbackSkill()).thenReturn(fallbackSkill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(responseValidationService.isAdequate(any(), any())).thenReturn(false)
        whenever(dynamicAgentService.chat(eq(skill), any(), any(), any())).thenReturn(
            AgentResponse(response = "I can't help with that")
        )
        whenever(dynamicAgentService.chat(eq(fallbackSkill), any(), any(), any())).thenReturn(
            AgentResponse(response = "Here's the answer!")
        )

        val emitter = service.streamChat("What is 2+2?", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(dynamicAgentService).chat(eq(skill), any(), any(), any())
        verify(dynamicAgentService).chat(eq(fallbackSkill), any(), any(), any())
        verify(skillRouterService).findFallbackSkill()
    }

    @Test
    fun `skips validation when skill is already fallback`() {
        val latch = CountDownLatch(1)
        val fallbackSkill = Skill(
            name = "general-assistant",
            description = "general",
            systemPrompt = "You are a general assistant.",
            toolNames = emptyList()
        )

        whenever(skillRouterService.route(any(), any())).thenReturn(fallbackSkill)
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(dynamicAgentService.chat(any(), any(), any(), any())).thenReturn(
            AgentResponse(response = "Hello!")
        )

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        verify(responseValidationService, never()).isAdequate(any(), any())
    }

    @Test
    fun `stores user message but not assistant message when agent loop throws`() {
        val latch = CountDownLatch(1)
        val storedMessages = mutableListOf<ConversationMessage>()

        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        doAnswer { invocation ->
            storedMessages.add(invocation.getArgument(1))
            Unit
        }.whenever(conversationMemoryService).addMessage(any(), any())
        whenever(skillRouterService.route(any(), any())).thenThrow(RuntimeException("Routing failed"))

        val emitter = service.streamChat("Hi", null)
        emitter.onCompletion { latch.countDown() }
        emitter.onError { latch.countDown() }

        latch.await(5, TimeUnit.SECONDS)

        // User message is stored before routing, but assistant message should not be stored
        assertEquals(1, storedMessages.size)
        assertEquals("user", storedMessages[0].role)
    }
}
