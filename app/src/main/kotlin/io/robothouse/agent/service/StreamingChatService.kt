package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Handles streaming chat interactions by running the agent loop on a
 * virtual thread and forwarding events to an [SseEmitter] as JSON.
 *
 * Emits a [AgentEvent.SkillMatchedEvent] after routing, then relays all
 * events from the agent loop via an [AgentEventListener]. On failure,
 * sends an [AgentEvent.ErrorEvent] before completing the emitter with an error.
 */
@Service
class StreamingChatService(
    private val skillRouterService: SkillRouterService,
    private val dynamicAgentService: DynamicAgentService,
    private val agentProperties: AgentProperties,
    private val objectMapper: ObjectMapper
) {

    /**
     * Creates an SSE emitter that streams typed agent events for the given message.
     *
     * Routes the message to a skill, then executes the agent loop on a virtual
     * thread, serializing each [AgentEvent] as a named SSE event. The emitter
     * timeout is derived from the configured tool execution timeout plus a buffer.
     */
    fun streamChat(userMessage: String): SseEmitter {
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000 + 5000L
        val emitter = SseEmitter(timeoutMillis)

        emitter.onTimeout {
            log.warn { "SSE emitter timed out" }
        }
        emitter.onError { ex ->
            log.warn { "SSE emitter error: ${ex.message}" }
        }

        Thread.startVirtualThread {
            try {
                val skill = skillRouterService.route(userMessage)

                val listener = AgentEventListener { event ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name(event.type)
                                .data(objectMapper.writeValueAsString(event))
                        )
                    } catch (e: Exception) {
                        log.debug { "Failed to send SSE event: ${e.message}" }
                    }
                }

                listener.onEvent(AgentEvent.SkillMatchedEvent(skillName = skill.name))
                dynamicAgentService.chat(skill, userMessage, listener)
                emitter.complete()
            } catch (e: Exception) {
                log.warn { "SSE stream error: ${e.message}" }
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(AgentEvent.ErrorEvent(message = e.message ?: "Unknown error")))
                    )
                } catch (_: Exception) { }
                emitter.completeWithError(e)
            }
        }

        return emitter
    }
}
