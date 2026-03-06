package io.robothouse.agent.listener

import io.robothouse.agent.model.AgentEvent

/**
 * Callback interface for observing events emitted during the agent loop.
 *
 * Implementations receive typed [AgentEvent] instances at each stage of
 * execution (iteration start, tool calls, thoughts, plan steps, etc.).
 * Listener exceptions are caught by the caller and never interrupt the
 * agent loop.
 */
fun interface AgentEventListener {
    fun onEvent(event: AgentEvent)

    companion object {
        /** No-op listener that silently discards all events. */
        val NOOP = AgentEventListener { }
    }
}
