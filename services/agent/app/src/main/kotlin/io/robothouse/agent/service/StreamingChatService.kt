package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.graph.orchestration.OrchestrationGraphBuilder
import io.robothouse.agent.graph.orchestration.OrchestrationGraphContext
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState
import io.robothouse.agent.graph.unwrapGraphException
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Handles streaming chat interactions by running the orchestration graph
 * on a virtual thread and forwarding events to an [SseEmitter] as JSON.
 *
 * Delegates conversation orchestration (memory loading, skill routing,
 * execution, validation, and fallback rerouting) to a LangGraph4j
 * StateGraph built by [OrchestrationGraphBuilder]. Manages SSE lifecycle,
 * heartbeat, and assistant message persistence after graph completion.
 */
@Service
class StreamingChatService(
    private val skillRouterService: SkillRouterService,
    private val dynamicAgentService: DynamicAgentService,
    private val conversationMemoryService: ConversationMemoryService,
    private val responseValidationService: ResponseValidationService,
    private val agentProperties: AgentProperties,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private const val HEARTBEAT_INTERVAL_SECONDS = 30L
    }

    private val heartbeatScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread.ofVirtual().name("sse-heartbeat").unstarted(runnable)
    }

    /**
     * Creates an SSE emitter that streams typed agent events for the given message.
     *
     * Builds an orchestration graph that handles memory loading, skill routing,
     * execution, response validation, and optional fallback rerouting. Each
     * [AgentEvent] is serialized as a named SSE event. The assistant response
     * is stored to Redis after the graph completes. The emitter timeout is
     * derived from the configured tool execution timeout plus a buffer.
     */
    fun streamChat(userMessage: String, conversationId: String?): SseEmitter {
        val resolvedConversationId = conversationId ?: UUID.randomUUID().toString()
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000 + 5000L
        val emitter = SseEmitter(timeoutMillis)

        emitter.onTimeout { log.warn { "SSE emitter timed out" } }
        emitter.onError { ex -> log.warn { "SSE emitter error: ${ex.message}" } }

        Thread.ofVirtual()
            .uncaughtExceptionHandler { _, ex -> log.error(ex) { "Uncaught exception on SSE virtual thread" } }
            .start {
                val heartbeat = heartbeatScheduler.scheduleAtFixedRate({
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name("heartbeat")
                                .data(objectMapper.writeValueAsString(AgentEvent.HeartbeatEvent()))
                        )
                    } catch (_: Exception) {
                        // Emitter already closed — heartbeat will be cancelled shortly
                    }
                }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)

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

                    val ctx = OrchestrationGraphContext(
                        skillRouterService = skillRouterService,
                        dynamicAgentService = dynamicAgentService,
                        conversationMemoryService = conversationMemoryService,
                        responseValidationService = responseValidationService,
                        listener = listener
                    )

                    val compiledGraph = OrchestrationGraphBuilder.build(ctx)

                    val initialState = mapOf<String, Any>(
                        OrchestrationGraphState.CONVERSATION_ID to resolvedConversationId,
                        OrchestrationGraphState.USER_MESSAGE to userMessage
                    )

                    val finalState = try {
                        compiledGraph.invoke(initialState).orElseThrow {
                            log.warn { "Orchestration graph produced no final state for conversation: id=$resolvedConversationId" }
                            IllegalStateException("Orchestration graph produced no final state")
                        }
                    } catch (e: Exception) {
                        val unwrapped = unwrapGraphException(e)
                        log.warn { "Orchestration graph execution failed for conversation: id=$resolvedConversationId, error=${unwrapped.message}" }
                        throw unwrapped
                    }

                    val result = OrchestrationGraphState(finalState.data())

                    try {
                        conversationMemoryService.addMessage(
                            resolvedConversationId,
                            ConversationMessage(
                                role = "assistant",
                                content = result.agentResponse.response,
                                skill = result.agentResponse.skill,
                                activities = activities.toList()
                            )
                        )
                    } catch (e: Exception) {
                        log.warn { "Failed to store assistant response: ${e.message}" }
                        listener.onEvent(AgentEvent.WarningEvent(message = "Failed to save this response to conversation memory"))
                    }

                    heartbeat.cancel(false)
                    emitter.complete()
                } catch (e: Exception) {
                    heartbeat.cancel(false)
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
