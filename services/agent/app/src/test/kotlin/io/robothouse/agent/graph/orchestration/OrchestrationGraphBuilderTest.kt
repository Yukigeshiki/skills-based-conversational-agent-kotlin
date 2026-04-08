package io.robothouse.agent.graph.orchestration

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.service.ConversationMemoryService
import io.robothouse.agent.service.DynamicAgentService
import io.robothouse.agent.service.ResponseValidationService
import io.robothouse.agent.service.SkillRouterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Collections

class OrchestrationGraphBuilderTest {

    private val skillRouterService: SkillRouterService = mock()
    private val dynamicAgentService: DynamicAgentService = mock()
    private val conversationMemoryService: ConversationMemoryService = mock()
    private val responseValidationService: ResponseValidationService = mock()

    private val fallbackSkill = Skill(
        name = SkillRouterService.FALLBACK_SKILL_NAME,
        description = "general assistant",
        systemPrompt = "Answer questions concisely and accurately.",
        toolNames = emptyList()
    )

    private val specialistSkill = Skill(
        name = "horticulturalist",
        description = "gardening expert",
        systemPrompt = "You are a gardening expert.",
        toolNames = emptyList()
    )

    private fun captureEvents(): Pair<AgentEventListener, MutableList<AgentEvent>> {
        val events = Collections.synchronizedList(mutableListOf<AgentEvent>())
        val listener = AgentEventListener { event -> events.add(event) }
        return listener to events
    }

    private fun buildContext(listener: AgentEventListener) = OrchestrationGraphContext(
        skillRouterService = skillRouterService,
        dynamicAgentService = dynamicAgentService,
        conversationMemoryService = conversationMemoryService,
        responseValidationService = responseValidationService,
        listener = listener
    )

    private fun initialState(userMessage: String = "Tell me about physics", conversationId: String = "conv-1") =
        mapOf(
            OrchestrationGraphState.CONVERSATION_ID to conversationId,
            OrchestrationGraphState.USER_MESSAGE to userMessage
        )

    @Test
    fun `fallback skill response is validated and final event is emitted when adequate`() {
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(skillRouterService.route(any(), any())).thenReturn(fallbackSkill)
        whenever(dynamicAgentService.chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            val listener = invocation.getArgument<AgentEventListener>(2)
            // Simulate the fast-path final response event being emitted by the agent.
            listener.onEvent(AgentEvent.FinalResponseEvent(response = "Physics is the study of matter and energy.", skill = fallbackSkill.name))
            AgentResponse(response = "Physics is the study of matter and energy.", skill = fallbackSkill.name)
        }
        whenever(responseValidationService.isAdequate(any(), any())).thenReturn(true)

        val (listener, events) = captureEvents()
        val graph = OrchestrationGraphBuilder.build(buildContext(listener))

        graph.invoke(initialState())

        // Validation must run for the fallback skill — bug 2 was that this was skipped.
        verify(responseValidationService).isAdequate(eq("Tell me about physics"), eq("Physics is the study of matter and energy."))
        // No reroute event should be emitted when validation passes.
        assertTrue(events.none { it is AgentEvent.SkillReroutedEvent })
        // The held final response event must be released after validation passes.
        assertTrue(events.any { it is AgentEvent.FinalResponseEvent })
        // Exactly one skill chat call (no retry).
        verify(dynamicAgentService, org.mockito.kotlin.times(1)).chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }

    /** Captured arguments for a single dynamicAgentService.chat invocation. */
    private data class ChatCall(
        val skill: Skill,
        val userMessage: String,
        val history: List<ConversationMessage>,
        val systemPromptSuffix: String?
    )

    @Test
    fun `inadequate fallback response triggers a directive retry`() {
        whenever(conversationMemoryService.getHistory(any())).thenReturn(
            listOf(
                ConversationMessage(role = "user", content = "How do I grow potatoes?"),
                ConversationMessage(role = "assistant", content = "Plant in well-drained soil.", skill = "horticulturalist")
            )
        )
        whenever(skillRouterService.route(any(), any())).thenReturn(fallbackSkill)
        whenever(skillRouterService.findFallbackSkill()).thenReturn(fallbackSkill)

        // First call: fallback returns a refusal-style response.
        // Second call (retry): returns a real answer. Capture all call arguments
        // manually so we can assert against the (nullable) systemPromptSuffix.
        val chatCalls = Collections.synchronizedList(mutableListOf<ChatCall>())
        whenever(dynamicAgentService.chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            chatCalls.add(
                ChatCall(
                    skill = invocation.getArgument(0),
                    userMessage = invocation.getArgument(1),
                    history = invocation.getArgument(3) as List<ConversationMessage>,
                    systemPromptSuffix = invocation.getArgument(5) as String?
                )
            )
            val listener = invocation.getArgument<AgentEventListener>(2)
            if (chatCalls.size == 1) {
                // First call: emit the inadequate response event into the listener (it will
                // be held by the gating wrapper since this is not the fallback's terminal
                // path before validation).
                listener.onEvent(AgentEvent.FinalResponseEvent(response = "I cannot help with that.", skill = fallbackSkill.name))
                AgentResponse(response = "I cannot help with that.", skill = fallbackSkill.name)
            } else {
                AgentResponse(response = "Physics is the study of matter and energy.", skill = fallbackSkill.name)
            }
        }
        whenever(responseValidationService.isAdequate(any(), any())).thenReturn(false)

        val (listener, events) = captureEvents()
        val graph = OrchestrationGraphBuilder.build(buildContext(listener))

        graph.invoke(initialState())

        assertEquals(2, chatCalls.size, "expected one initial call + one directive retry")

        // First call: fallback skill, original history, no suffix.
        val first = chatCalls[0]
        assertEquals(fallbackSkill.name, first.skill.name)
        assertEquals(2, first.history.size)
        assertNull(first.systemPromptSuffix)

        // Second call: fallback skill, EMPTY history, non-null suffix containing the directive.
        val second = chatCalls[1]
        assertEquals(fallbackSkill.name, second.skill.name)
        assertTrue(second.history.isEmpty(), "directive retry must use empty conversation history")
        assertNotNull(second.systemPromptSuffix)
        assertTrue(
            second.systemPromptSuffix!!.contains("IMPORTANT"),
            "directive suffix must contain the IMPORTANT marker, got: ${second.systemPromptSuffix}"
        )

        // SkillReroutedEvent must fire with a reason that distinguishes the directive-retry path.
        val rerouted = events.filterIsInstance<AgentEvent.SkillReroutedEvent>()
        assertEquals(1, rerouted.size)
        assertEquals(fallbackSkill.name, rerouted[0].fromSkill)
        assertEquals(fallbackSkill.name, rerouted[0].toSkill)
        assertTrue(
            rerouted[0].reason.contains("directive", ignoreCase = true) || rerouted[0].reason.contains("retry", ignoreCase = true),
            "expected directive-retry reason, got: ${rerouted[0].reason}"
        )

        // The inadequate "I cannot help with that." final response must never have been
        // released to the listener — it was held during validation and superseded by the
        // retry path.
        val finalEvents = events.filterIsInstance<AgentEvent.FinalResponseEvent>()
        assertTrue(
            finalEvents.none { it.response == "I cannot help with that." },
            "the inadequate held final response must never reach the listener"
        )
    }

    @Test
    fun `inadequate specialist response reroutes to fallback with original history`() {
        // Regression for the existing path: specialist → fallback rerouting must still
        // pass the original conversation history (no directive suffix).
        val priorHistory = listOf(
            ConversationMessage(role = "user", content = "Hi"),
            ConversationMessage(role = "assistant", content = "Hello.", skill = "horticulturalist")
        )
        whenever(conversationMemoryService.getHistory(any())).thenReturn(priorHistory)
        whenever(skillRouterService.route(any(), any())).thenReturn(specialistSkill)
        whenever(skillRouterService.findFallbackSkill()).thenReturn(fallbackSkill)

        val chatCalls = Collections.synchronizedList(mutableListOf<ChatCall>())
        whenever(dynamicAgentService.chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            chatCalls.add(
                ChatCall(
                    skill = invocation.getArgument(0),
                    userMessage = invocation.getArgument(1),
                    history = invocation.getArgument(3) as List<ConversationMessage>,
                    systemPromptSuffix = invocation.getArgument(5) as String?
                )
            )
            AgentResponse(response = "stub", skill = (invocation.getArgument(0) as Skill).name)
        }
        whenever(responseValidationService.isAdequate(any(), any())).thenReturn(false)

        val (listener, events) = captureEvents()
        val graph = OrchestrationGraphBuilder.build(buildContext(listener))

        graph.invoke(initialState())

        assertEquals(2, chatCalls.size)

        // First call: specialist with original history, no suffix.
        assertEquals(specialistSkill.name, chatCalls[0].skill.name)
        assertEquals(2, chatCalls[0].history.size)
        assertNull(chatCalls[0].systemPromptSuffix)

        // Second call (reroute): fallback with the SAME original history, no suffix.
        assertEquals(fallbackSkill.name, chatCalls[1].skill.name)
        assertEquals(2, chatCalls[1].history.size)
        assertNull(chatCalls[1].systemPromptSuffix)

        val rerouted = events.filterIsInstance<AgentEvent.SkillReroutedEvent>()
        assertEquals(1, rerouted.size)
        assertEquals(specialistSkill.name, rerouted[0].fromSkill)
        assertEquals(fallbackSkill.name, rerouted[0].toSkill)
        // Specialist→fallback reason must NOT mention "directive" — that label is reserved
        // for the fallback→fallback retry path.
        assertFalse(rerouted[0].reason.contains("directive", ignoreCase = true))
    }

    @Test
    fun `awaiting approval bypasses validation regardless of matched skill`() {
        // The pre-existing approval-bypass path must still work after the fallback
        // early-exit was removed.
        whenever(conversationMemoryService.getHistory(any())).thenReturn(emptyList())
        whenever(skillRouterService.route(any(), any())).thenReturn(specialistSkill)
        whenever(dynamicAgentService.chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            AgentResponse(response = "Awaiting human approval", skill = specialistSkill.name, awaitingApproval = true)
        )

        val (listener, _) = captureEvents()
        val graph = OrchestrationGraphBuilder.build(buildContext(listener))

        graph.invoke(initialState())

        verify(responseValidationService, never()).isAdequate(any(), any())
        verify(dynamicAgentService, org.mockito.kotlin.times(1)).chat(any(), any(), any(), any(), anyOrNull(), anyOrNull())
    }
}
