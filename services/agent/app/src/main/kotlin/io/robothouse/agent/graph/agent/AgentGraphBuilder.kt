package io.robothouse.agent.graph.agent

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.graph.InMemoryStateSerializer
import io.robothouse.agent.graph.agent.AgentGraphState.Companion.DONE
import io.robothouse.agent.graph.agent.AgentGraphState.Companion.ITERATION
import io.robothouse.agent.graph.agent.AgentGraphState.Companion.MESSAGES
import io.robothouse.agent.graph.agent.AgentGraphState.Companion.RESPONSE
import io.robothouse.agent.graph.agent.AgentGraphState.Companion.STEPS
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentIteration
import io.robothouse.agent.model.ToolCall
import io.robothouse.agent.model.ToolExecutionStep
import io.robothouse.agent.model.ToolObservation
import org.bsc.langgraph4j.CompiledGraph
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.GraphDefinition.END
import org.bsc.langgraph4j.GraphDefinition.START
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
import org.bsc.langgraph4j.action.AsyncNodeAction.node_async

/**
 * Builds and compiles a LangGraph4j StateGraph that implements the agent
 * tool-execution loop (call LLM, execute tools, repeat).
 *
 * The graph has two nodes forming a cycle:
 * - call_llm: calls the LLM and decides whether to continue or finish
 * - execute_tools: executes requested tools and feeds results back
 *
 * Graph topology:
 *   START -> call_llm -> conditional -> execute_tools -> call_llm (cycle)
 *                                   -> END (when done)
 *
 * Infrastructure dependencies (chat model, tool executors, event listener, etc.)
 * are captured in node closures via [AgentGraphContext] rather than stored in graph
 * state. Only data that changes between nodes lives in [AgentGraphState].
 */
object AgentGraphBuilder {

    private const val CALL_LLM = "call_llm"
    private const val EXECUTE_TOOLS = "execute_tools"

    /**
     * Builds and compiles the agent loop graph from the given [ctx].
     *
     * Returns a compiled graph ready for synchronous invocation. The graph
     * is compiled fresh per call because the node closures capture the
     * per-invocation context.
     */
    fun build(ctx: AgentGraphContext): CompiledGraph<AgentGraphState> {
        val serializer = InMemoryStateSerializer(::AgentGraphState)
        val graph = StateGraph(AgentGraphState.SCHEMA, serializer)

        graph.addNode(CALL_LLM, node_async { state: AgentGraphState ->
            callLlm(state, ctx)
        })

        graph.addNode(EXECUTE_TOOLS, node_async { state: AgentGraphState ->
            executeTools(state, ctx)
        })

        graph.addEdge(START, CALL_LLM)

        graph.addConditionalEdges(
            CALL_LLM,
            edge_async { state: AgentGraphState -> if (state.done) "end" else "continue" },
            mapOf("end" to END, "continue" to EXECUTE_TOOLS)
        )

        graph.addEdge(EXECUTE_TOOLS, CALL_LLM)

        // Each iteration visits call_llm + execute_tools (2 nodes), plus one final
        // call_llm that detects max iterations exceeded and exits. The multiplier
        // includes margin beyond the theoretical max of (maxIterations * 2 + 1)
        // as a safety net — actual iteration control is enforced in the call_llm node.
        return graph.compile(
            CompileConfig.builder()
                .recursionLimit(ctx.maxIterations * 3 + 1)
                .build()
        )
    }

    /**
     * Handles a single LLM call iteration. If the iteration limit has been
     * exceeded, returns a fallback response and marks the graph as done.
     * Otherwise, injects the scratchpad (on iteration 2+), calls the LLM,
     * and either completes with a text response or signals that tool
     * execution is needed.
     */
    private fun callLlm(
        state: AgentGraphState,
        ctx: AgentGraphContext
    ): Map<String, Any> {
        val iteration = state.iteration

        if (iteration > ctx.maxIterations) {
            val lastAiText = state.messages.filterIsInstance<AiMessage>().lastOrNull()?.text() ?: ""
            val response = lastAiText.ifBlank { "I reached the maximum number of tool executions. Here's what I found so far." }
            if (ctx.emitFinalResponse) {
                ctx.emitEvent(ctx.listener) { AgentEvent.FinalResponseEvent(response = response, skill = ctx.skillName) }
            }
            return mapOf(DONE to true, RESPONSE to response)
        }

        ctx.checkTimeout(ctx.startTime, ctx.timeoutMillis)

        val messages = state.messages.toMutableList()
        if (iteration >= 2) {
            val scratchpad = ctx.taskMemory.toScratchpad()
            if (scratchpad != null) {
                messages[0] = SystemMessage.from("${ctx.systemPrompt}\n\n## Your work so far\n$scratchpad")
            }
        }

        ctx.emitEvent(ctx.listener) { AgentEvent.IterationStartedEvent(iterationNumber = iteration) }

        val requestBuilder = ChatRequest.builder().messages(messages)
        if (ctx.specifications.isNotEmpty()) {
            requestBuilder.toolSpecifications(ctx.specifications)
        }

        val response = ctx.agentChatModel.chat(requestBuilder.build())
        val aiMessage = response.aiMessage()
        messages.add(aiMessage)

        if (!aiMessage.hasToolExecutionRequests()) {
            ctx.taskMemory.addIteration(
                AgentIteration(iterationNumber = iteration, thought = aiMessage.text())
            )
            val responseText = aiMessage.text() ?: ""
            if (ctx.emitFinalResponse) {
                ctx.emitEvent(ctx.listener) {
                    AgentEvent.FinalResponseEvent(response = responseText, skill = ctx.skillName)
                }
            }
            return mapOf(
                MESSAGES to messages.toList(),
                DONE to true,
                RESPONSE to responseText
            )
        }

        val thought = aiMessage.text()
        if (!thought.isNullOrBlank()) {
            ctx.emitEvent(ctx.listener) {
                AgentEvent.ThoughtEvent(iterationNumber = iteration, thought = thought)
            }
        }

        return mapOf(
            MESSAGES to messages.toList(),
            DONE to false
        )
    }

    /**
     * Executes all tool requests from the last LLM response. Each tool call
     * is delegated to the context's [AgentGraphContext.executeTool] function,
     * and results are appended to the message list as tool execution result
     * messages. The iteration counter is incremented for the next cycle.
     */
    private fun executeTools(
        state: AgentGraphState,
        ctx: AgentGraphContext
    ): Map<String, Any> {
        val iteration = state.iteration
        val messages = state.messages.toMutableList()
        val lastAiMessage = messages.last() as AiMessage

        val newSteps = mutableListOf<ToolExecutionStep>()
        val iterationToolCalls = mutableListOf<ToolCall>()
        val iterationObservations = mutableListOf<ToolObservation>()

        lastAiMessage.toolExecutionRequests().forEach { toolRequest ->
            val step = ctx.executeTool(toolRequest, ctx.executors, iteration, ctx.listener)
            newSteps.add(step)
            iterationToolCalls.add(ToolCall(toolName = step.toolName, arguments = step.arguments))
            iterationObservations.add(ToolObservation(toolName = step.toolName, result = step.result, error = step.error))
            messages.add(ToolExecutionResultMessage.from(toolRequest, step.result))
        }

        ctx.taskMemory.addIteration(
            AgentIteration(
                iterationNumber = iteration,
                thought = lastAiMessage.text(),
                toolCalls = iterationToolCalls,
                observations = iterationObservations
            )
        )

        return mapOf(
            MESSAGES to messages.toList(),
            STEPS to newSteps,
            ITERATION to iteration + 1
        )
    }
}
