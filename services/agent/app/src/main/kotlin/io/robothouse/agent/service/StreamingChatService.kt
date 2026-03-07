package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.Collections
import java.util.UUID

/**
 * Handles streaming chat interactions by running the agent loop on a
 * virtual thread and forwarding events to an [SseEmitter] as JSON.
 *
 * Manages the conversation lifecycle: resolves or generates a conversation ID,
 * persists user and assistant messages to Redis via [ConversationMemoryService],
 * and injects prior conversation history into the agent loop. Emits a
 * [AgentEvent.ConversationStartedEvent] before routing, then relays all events
 * from the agent loop via an [AgentEventListener]. On failure, sends an
 * [AgentEvent.ErrorEvent] before completing the emitter.
 */
@Service
class StreamingChatService(
    private val skillRouterService: SkillRouterService,
    private val dynamicAgentService: DynamicAgentService,
    private val conversationMemoryService: ConversationMemoryService,
    private val agentProperties: AgentProperties,
    private val objectMapper: ObjectMapper
) {

    /**
     * Creates an SSE emitter that streams typed agent events for the given message.
     *
     * Resolves the conversation ID (generating one if absent), stores the user
     * message in Redis, loads prior history, routes to a skill, and executes
     * the agent loop on a virtual thread. Each [AgentEvent] is serialized as
     * a named SSE event. The assistant response is stored after completion.
     * The emitter timeout is derived from the configured tool execution timeout
     * plus a buffer.
     */
    fun streamChat(userMessage: String, conversationId: String?): SseEmitter {
        val resolvedConversationId = conversationId ?: UUID.randomUUID().toString()
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000 + 5000L
        val emitter = SseEmitter(timeoutMillis)

        emitter.onTimeout { log.warn { "SSE emitter timed out" } }
        emitter.onError { ex -> log.warn { "SSE emitter error: ${ex.message}" } }

        Thread.startVirtualThread {
            try {
                val activities = Collections.synchronizedList(mutableListOf<AgentEvent>())
                val listener = AgentEventListener { event ->
                    activities.add(event)
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name(event.type)
                                .data(objectMapper.writeValueAsString(event))
                        )
                    } catch (e: Exception) {
                        log.warn { "Failed to send SSE event: ${e.message}" }
                    }
                }

                listener.onEvent(AgentEvent.ConversationStartedEvent(conversationId = resolvedConversationId))

                val history = try {
                    val prior = conversationMemoryService.getHistory(resolvedConversationId)
                    conversationMemoryService.addMessage(
                        resolvedConversationId,
                        ConversationMessage(role = "user", content = userMessage)
                    )
                    prior
                } catch (e: Exception) {
                    log.warn { "Conversation memory unavailable: ${e.message}" }
                    listener.onEvent(AgentEvent.WarningEvent(message = "Conversation memory is unavailable — this response won't include prior context"))
                    emptyList()
                }

                val skill = skillRouterService.route(userMessage)
                listener.onEvent(AgentEvent.SkillMatchedEvent(skillName = skill.name))

                val response = dynamicAgentService.chat(skill, userMessage, listener, history)

                try {
                    conversationMemoryService.addMessage(
                        resolvedConversationId,
                        ConversationMessage(role = "assistant", content = response.response, activities = activities.toList())
                    )
                } catch (e: Exception) {
                    log.warn { "Failed to store assistant response: ${e.message}" }
                    listener.onEvent(AgentEvent.WarningEvent(message = "Failed to save this response to conversation memory"))
                }

                emitter.complete()
            } catch (e: Exception) {
                log.warn { "SSE stream error: ${e.message}" }
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(AgentEvent.ErrorEvent(message = e.message ?: "Unknown error")))
                    )
                    emitter.complete()
                } catch (sendError: Exception) {
                    log.warn { "Failed to send error event to client: ${sendError.message}" }
                    emitter.completeWithError(e)
                }
            }
        }

        return emitter
    }
}
