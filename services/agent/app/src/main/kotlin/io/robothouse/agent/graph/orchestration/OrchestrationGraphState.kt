package io.robothouse.agent.graph.orchestration

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ConversationMessage
import org.bsc.langgraph4j.state.AgentState
import org.bsc.langgraph4j.state.Channel
import org.bsc.langgraph4j.state.Channels

/**
 * Graph state for the orchestration flow that routes a user message through
 * memory loading, skill routing, execution, validation, and optional fallback.
 *
 * Contains only data that changes between node executions. Service
 * dependencies and the event listener are captured in node closures
 * via [OrchestrationGraphContext] instead.
 */
class OrchestrationGraphState(initData: Map<String, Any>) : AgentState(initData) {

    companion object {
        const val CONVERSATION_ID = "conversationId"
        const val USER_MESSAGE = "userMessage"
        const val CONVERSATION_HISTORY = "conversationHistory"
        const val MATCHED_SKILL = "matchedSkill"
        const val AGENT_RESPONSE = "agentResponse"
        const val VALIDATED = "validated"

        val SCHEMA: Map<String, Channel<*>> = mapOf(
            CONVERSATION_ID to Channels.base<String> { _, new -> new },
            USER_MESSAGE to Channels.base<String> { _, new -> new },
            CONVERSATION_HISTORY to Channels.base<List<ConversationMessage>> { _, new -> new },
            MATCHED_SKILL to Channels.base<Skill> { _, new -> new },
            AGENT_RESPONSE to Channels.base<AgentResponse> { _, new -> new },
            VALIDATED to Channels.base<Boolean> { _, new -> new }
        )
    }

    val conversationId: String
        get() = value<String>(CONVERSATION_ID).orElse("")

    val userMessage: String
        get() = value<String>(USER_MESSAGE).orElse("")

    val conversationHistory: List<ConversationMessage>
        get() = value<List<ConversationMessage>>(CONVERSATION_HISTORY).orElse(emptyList())

    val matchedSkill: Skill
        get() = value<Skill>(MATCHED_SKILL).orElseThrow {
            IllegalStateException("matchedSkill has not been set")
        }

    val agentResponse: AgentResponse
        get() = value<AgentResponse>(AGENT_RESPONSE).orElseThrow {
            IllegalStateException("agentResponse has not been set")
        }

    val validated: Boolean
        get() = value<Boolean>(VALIDATED).orElse(false)
}
