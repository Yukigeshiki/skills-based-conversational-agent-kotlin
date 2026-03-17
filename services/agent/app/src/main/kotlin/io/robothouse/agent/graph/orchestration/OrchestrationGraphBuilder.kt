package io.robothouse.agent.graph.orchestration

import io.robothouse.agent.graph.InMemoryStateSerializer
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState.Companion.AGENT_RESPONSE
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState.Companion.CONVERSATION_HISTORY
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState.Companion.MATCHED_SKILL
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState.Companion.VALIDATED
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.service.SkillRouterService
import io.robothouse.agent.util.log
import org.bsc.langgraph4j.CompiledGraph
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.GraphDefinition.END
import org.bsc.langgraph4j.GraphDefinition.START
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
import org.bsc.langgraph4j.action.AsyncNodeAction.node_async
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds and compiles a LangGraph4j StateGraph that implements the
 * orchestration flow for a single chat request.
 *
 * The graph models the sequence: load conversation memory, route to a
 * skill, execute the skill, validate the response (for specialist skills),
 * and optionally reroute to the fallback skill.
 *
 * Graph topology:
 *   START -> load_memory -> route_skill -> execute_skill -> conditional
 *                                                            -> END (fallback skill)
 *                                                            -> validate_response -> conditional
 *                                                                                    -> END (adequate)
 *                                                                                    -> reroute_fallback -> END
 */
object OrchestrationGraphBuilder {

    private const val LOAD_MEMORY = "load_memory"
    private const val ROUTE_SKILL = "route_skill"
    private const val EXECUTE_SKILL = "execute_skill"
    private const val VALIDATE_RESPONSE = "validate_response"
    private const val REROUTE_FALLBACK = "reroute_fallback"

    /**
     * Builds and compiles the orchestration graph from the given [ctx].
     *
     * The held [AgentEvent.FinalResponseEvent] reference is shared between
     * the execute_skill and validate_response closures so that the event
     * can be withheld during validation and emitted only when the response
     * is deemed adequate.
     */
    fun build(ctx: OrchestrationGraphContext): CompiledGraph<OrchestrationGraphState> {
        // Nullable holder shared between execute_skill and validate_response closures.
        // AtomicReference is used for its nullable holder semantics, not thread safety —
        // the graph executes synchronously on a single thread.
        val heldFinalEvent = AtomicReference<AgentEvent.FinalResponseEvent?>()

        val serializer = ctx.stateSerializer
            ?: InMemoryStateSerializer(::OrchestrationGraphState)
        val graph = StateGraph(OrchestrationGraphState.SCHEMA, serializer)

        graph.addNode(LOAD_MEMORY, node_async { state: OrchestrationGraphState ->
            loadMemory(state, ctx)
        })

        graph.addNode(ROUTE_SKILL, node_async { state: OrchestrationGraphState ->
            routeSkill(state, ctx)
        })

        graph.addNode(EXECUTE_SKILL, node_async { state: OrchestrationGraphState ->
            executeSkill(state, ctx, heldFinalEvent)
        })

        graph.addNode(VALIDATE_RESPONSE, node_async { state: OrchestrationGraphState ->
            validateResponse(state, ctx, heldFinalEvent)
        })

        graph.addNode(REROUTE_FALLBACK, node_async { state: OrchestrationGraphState ->
            rerouteFallback(state, ctx)
        })

        graph.addEdge(START, LOAD_MEMORY)
        graph.addEdge(LOAD_MEMORY, ROUTE_SKILL)
        graph.addEdge(ROUTE_SKILL, EXECUTE_SKILL)

        graph.addConditionalEdges(
            EXECUTE_SKILL,
            edge_async { state: OrchestrationGraphState ->
                if (state.matchedSkill.name == SkillRouterService.FALLBACK_SKILL_NAME) "done" else "validate"
            },
            mapOf("done" to END, "validate" to VALIDATE_RESPONSE)
        )

        graph.addConditionalEdges(
            VALIDATE_RESPONSE,
            edge_async { state: OrchestrationGraphState ->
                if (state.validated) "done" else "reroute"
            },
            mapOf("done" to END, "reroute" to REROUTE_FALLBACK)
        )

        graph.addEdge(REROUTE_FALLBACK, END)

        // The graph is acyclic, so the recursion limit is just a safety net.
        val compileConfig = CompileConfig.builder()
            .recursionLimit(10)
        ctx.checkpointSaver?.let { compileConfig.checkpointSaver(it) }
        return graph.compile(compileConfig.build())
    }

    /**
     * Loads conversation history from Redis and stores the user message.
     * Emits a [AgentEvent.ConversationStartedEvent] at the start. On memory
     * failure, emits a [AgentEvent.WarningEvent] and continues with empty history.
     */
    private fun loadMemory(
        state: OrchestrationGraphState,
        ctx: OrchestrationGraphContext
    ): Map<String, Any> {
        val conversationId = state.conversationId
        val userMessage = state.userMessage

        ctx.listener.onEvent(AgentEvent.ConversationStartedEvent(conversationId = conversationId))

        val history = try {
            ctx.conversationMemoryService.getHistory(conversationId)
        } catch (e: Exception) {
            log.warn { "Failed to load conversation history: ${e.message}" }
            ctx.listener.onEvent(
                AgentEvent.WarningEvent(message = "Conversation memory is unavailable — this response won't include prior context")
            )
            emptyList()
        }

        try {
            ctx.conversationMemoryService.addMessage(
                conversationId,
                ConversationMessage(role = "user", content = userMessage)
            )
        } catch (e: Exception) {
            log.warn { "Failed to store user message: ${e.message}" }
        }

        return mapOf(CONVERSATION_HISTORY to history)
    }

    /**
     * Routes the user message to the most relevant skill and emits a
     * [AgentEvent.SkillMatchedEvent].
     */
    private fun routeSkill(
        state: OrchestrationGraphState,
        ctx: OrchestrationGraphContext
    ): Map<String, Any> {
        val skill = ctx.skillRouterService.route(state.userMessage, state.conversationHistory)
        ctx.listener.onEvent(AgentEvent.SkillMatchedEvent(skillName = skill.name))
        return mapOf(MATCHED_SKILL to skill)
    }

    /**
     * Executes the matched skill via the dynamic agent service. For specialist
     * skills, uses a gated listener that holds back the [AgentEvent.FinalResponseEvent]
     * so it can be conditionally emitted after validation. For fallback skills,
     * passes the original listener directly.
     */
    private fun executeSkill(
        state: OrchestrationGraphState,
        ctx: OrchestrationGraphContext,
        heldFinalEvent: AtomicReference<AgentEvent.FinalResponseEvent?>
    ): Map<String, Any> {
        val skill = state.matchedSkill
        val isFallback = skill.name == SkillRouterService.FALLBACK_SKILL_NAME

        val effectiveListener = if (isFallback) {
            ctx.listener
        } else {
            AgentEventListener { event ->
                if (event is AgentEvent.FinalResponseEvent) {
                    heldFinalEvent.set(event)
                } else {
                    ctx.listener.onEvent(event)
                }
            }
        }

        val response = ctx.dynamicAgentService.chat(
            skill, state.userMessage, effectiveListener, state.conversationHistory,
            conversationId = state.conversationId
        )

        return mapOf(AGENT_RESPONSE to response)
    }

    /**
     * Validates whether the specialist skill's response adequately answers the
     * user's question. If adequate, emits the held [AgentEvent.FinalResponseEvent].
     */
    private fun validateResponse(
        state: OrchestrationGraphState,
        ctx: OrchestrationGraphContext,
        heldFinalEvent: AtomicReference<AgentEvent.FinalResponseEvent?>
    ): Map<String, Any> {
        val adequate = ctx.responseValidationService.isAdequate(
            state.userMessage, state.agentResponse.response
        )

        if (adequate) {
            heldFinalEvent.get()?.let { ctx.listener.onEvent(it) }
        }

        return mapOf(VALIDATED to adequate)
    }

    /**
     * Reroutes to the fallback skill after a specialist response was deemed
     * inadequate. Emits [AgentEvent.SkillReroutedEvent] and
     * [AgentEvent.SkillMatchedEvent] before executing the fallback.
     */
    private fun rerouteFallback(
        state: OrchestrationGraphState,
        ctx: OrchestrationGraphContext
    ): Map<String, Any> {
        val fromSkill = state.matchedSkill.name

        log.info { "Rerouting from $fromSkill to ${SkillRouterService.FALLBACK_SKILL_NAME}" }
        ctx.listener.onEvent(AgentEvent.SkillReroutedEvent(fromSkill = fromSkill, toSkill = SkillRouterService.FALLBACK_SKILL_NAME))
        ctx.listener.onEvent(AgentEvent.SkillMatchedEvent(skillName = SkillRouterService.FALLBACK_SKILL_NAME))

        val fallbackSkill = ctx.skillRouterService.findFallbackSkill()
        val response = ctx.dynamicAgentService.chat(
            fallbackSkill, state.userMessage, ctx.listener, state.conversationHistory,
            conversationId = state.conversationId
        )

        return mapOf(AGENT_RESPONSE to response)
    }
}
