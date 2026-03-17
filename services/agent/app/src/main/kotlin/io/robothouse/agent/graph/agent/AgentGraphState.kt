package io.robothouse.agent.graph.agent

import dev.langchain4j.data.message.ChatMessage
import io.robothouse.agent.model.ToolExecutionStep
import org.bsc.langgraph4j.state.AgentState
import org.bsc.langgraph4j.state.Channel
import org.bsc.langgraph4j.state.Channels

/**
 * Graph state for the agent tool-execution loop.
 *
 * Contains only the data that changes between node executions. Constant
 * configuration (system prompt, tool specs, executors, listener, etc.)
 * is captured in the node closures via [AgentGraphContext] instead.
 */
class AgentGraphState(initData: Map<String, Any>) : AgentState(initData) {

    companion object {
        const val MESSAGES = "messages"
        const val STEPS = "steps"
        const val ITERATION = "iteration"
        const val RESPONSE = "response"
        const val DONE = "done"

        val SCHEMA: Map<String, Channel<*>> = mapOf(
            MESSAGES to Channels.base<List<ChatMessage>> { _, new -> new },
            STEPS to Channels.appenderWithDuplicate<ToolExecutionStep>(::ArrayList),
            ITERATION to Channels.base<Int> { _, new -> new },
            RESPONSE to Channels.base<String> { _, new -> new },
            DONE to Channels.base<Boolean> { _, new -> new }
        )
    }

    val messages: List<ChatMessage>
        get() = value<List<ChatMessage>>(MESSAGES).orElse(emptyList())

    val steps: List<ToolExecutionStep>
        get() = value<List<ToolExecutionStep>>(STEPS).orElse(emptyList())

    val iteration: Int
        get() = value<Int>(ITERATION).orElse(1)

    val response: String
        get() = value<String>(RESPONSE).orElse("")

    val done: Boolean
        get() = value<Boolean>(DONE).orElse(false)
}
