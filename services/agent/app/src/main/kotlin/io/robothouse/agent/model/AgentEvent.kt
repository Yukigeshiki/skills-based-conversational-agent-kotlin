package io.robothouse.agent.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

/**
 * Typed event hierarchy representing observable stages of the agent loop.
 *
 * Each subclass carries a [type] discriminator used as the SSE event name
 * and a [timestamp] recording when the event was created. Events are emitted
 * via [io.robothouse.agent.listener.AgentEventListener] and serialized as
 * JSON for streaming to clients.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AgentEvent.ConversationStartedEvent::class, name = "conversation_started"),
    JsonSubTypes.Type(value = AgentEvent.SkillMatchedEvent::class, name = "skill_matched"),
    JsonSubTypes.Type(value = AgentEvent.PlanCreatedEvent::class, name = "plan_created"),
    JsonSubTypes.Type(value = AgentEvent.PlanStepStartedEvent::class, name = "plan_step_started"),
    JsonSubTypes.Type(value = AgentEvent.PlanStepCompletedEvent::class, name = "plan_step_completed"),
    JsonSubTypes.Type(value = AgentEvent.IterationStartedEvent::class, name = "iteration_started"),
    JsonSubTypes.Type(value = AgentEvent.ThoughtEvent::class, name = "thought"),
    JsonSubTypes.Type(value = AgentEvent.ToolCallStartedEvent::class, name = "tool_call_started"),
    JsonSubTypes.Type(value = AgentEvent.ToolCallCompletedEvent::class, name = "tool_call_completed"),
    JsonSubTypes.Type(value = AgentEvent.FinalResponseEvent::class, name = "final_response"),
    JsonSubTypes.Type(value = AgentEvent.SkillReroutedEvent::class, name = "skill_rerouted"),
    JsonSubTypes.Type(value = AgentEvent.SkillHandoffStartedEvent::class, name = "skill_handoff_started"),
    JsonSubTypes.Type(value = AgentEvent.SkillHandoffCompletedEvent::class, name = "skill_handoff_completed"),
    JsonSubTypes.Type(value = AgentEvent.WarningEvent::class, name = "warning"),
    JsonSubTypes.Type(value = AgentEvent.ErrorEvent::class, name = "error"),
    JsonSubTypes.Type(value = AgentEvent.HeartbeatEvent::class, name = "heartbeat")
)
sealed class AgentEvent {
    abstract val type: String
    abstract val timestamp: Instant

    /**
     * Emitted at the start of a chat request with the resolved conversation ID,
     * allowing the client to track the conversation across requests.
     */
    data class ConversationStartedEvent(
        val conversationId: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "conversation_started"
    }

    /**
     * Emitted when skill routing completes and a skill has been selected
     * to handle the user's message.
     */
    data class SkillMatchedEvent(
        val skillName: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "skill_matched"
    }

    /**
     * Emitted when the task planning phase produces a multistep plan
     * for a skill that has a planning prompt.
     */
    data class PlanCreatedEvent(
        val plan: TaskPlan,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "plan_created"
    }

    /**
     * Emitted before execution of each step in a multistep plan.
     */
    data class PlanStepStartedEvent(
        val stepNumber: Int,
        val description: String,
        val skillName: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "plan_step_started"
    }

    /**
     * Emitted after a plan step finishes, carrying its completion status
     * and the response text produced by the agent loop for that step.
     */
    data class PlanStepCompletedEvent(
        val stepNumber: Int,
        val status: PlanStepStatus,
        val response: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "plan_step_completed"
    }

    /**
     * Emitted at the start of each agent loop iteration, before the LLM
     * is called for that iteration.
     */
    data class IterationStartedEvent(
        val iterationNumber: Int,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "iteration_started"
    }

    /**
     * Emitted when the LLM produces reasoning text, either as a standalone
     * response or alongside tool execution requests.
     */
    data class ThoughtEvent(
        val iterationNumber: Int,
        val thought: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "thought"
    }

    /**
     * Emitted before a tool is executed, capturing the tool name
     * and the JSON arguments the LLM provided.
     */
    data class ToolCallStartedEvent(
        val iterationNumber: Int,
        val toolName: String,
        val arguments: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "tool_call_started"
    }

    /**
     * Emitted after a tool execution finishes, carrying the result text.
     * When [error] is true, [result] contains the error message.
     */
    data class ToolCallCompletedEvent(
        val iterationNumber: Int,
        val toolName: String,
        val result: String,
        val error: Boolean = false,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "tool_call_completed"
    }

    /**
     * Emitted when the agent loop completes successfully, carrying
     * the final response text and matched skill name. Detailed iteration
     * and tool step data is omitted since it has already been streamed
     * via preceding events.
     */
    data class FinalResponseEvent(
        val response: String,
        val skill: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "final_response"
    }

    /**
     * Emitted when a specialist skill's response is deemed inadequate and the
     * request is rerouted to the fallback skill for a second attempt.
     */
    data class SkillReroutedEvent(
        val fromSkill: String,
        val toSkill: String,
        val reason: String = "Response did not adequately answer the question.",
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "skill_rerouted"
    }

    /**
     * Emitted when a skill delegates a request to another skill via the
     * delegateToSkill meta-tool, before the target skill begins execution.
     */
    data class SkillHandoffStartedEvent(
        val fromSkill: String,
        val toSkill: String,
        val request: String,
        val delegationDepth: Int,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "skill_handoff_started"
    }

    /**
     * Emitted after a delegated skill completes execution, carrying whether
     * the delegation succeeded or failed.
     */
    data class SkillHandoffCompletedEvent(
        val fromSkill: String,
        val toSkill: String,
        val delegationDepth: Int,
        val success: Boolean,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "skill_handoff_completed"
    }

    /**
     * Emitted when a non-fatal issue occurs that the user should be aware of,
     * such as conversation memory being unavailable. The agent continues
     * processing despite the warning.
     */
    data class WarningEvent(
        val message: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "warning"
    }

    /**
     * Emitted when an unrecoverable failure occurs during the agent loop
     * or skill routing, before the SSE stream is terminated.
     */
    data class ErrorEvent(
        val message: String,
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "error"
    }

    /**
     * Emitted periodically to keep the SSE connection alive during long
     * tool executions or LLM calls, preventing proxy/browser timeouts.
     */
    data class HeartbeatEvent(
        override val timestamp: Instant = Instant.now()
    ) : AgentEvent() {
        override val type: String = "heartbeat"
    }
}
