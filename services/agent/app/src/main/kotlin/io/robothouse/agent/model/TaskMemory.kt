package io.robothouse.agent.model

/**
 * A single iteration of the agent loop, capturing the LLM's reasoning,
 * the tool calls it requested, and the observations returned.
 */
data class AgentIteration(
    val iterationNumber: Int,
    val thought: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val observations: List<ToolObservation> = emptyList()
)

/**
 * A tool invocation requested by the LLM, recording the tool name
 * and the JSON arguments passed to it.
 */
data class ToolCall(
    val toolName: String,
    val arguments: String
)

/**
 * The result of a tool execution, including whether it succeeded or failed.
 *
 * When [error] is true, [result] contains the error message rather than
 * a successful output. Error observations are formatted differently in the
 * scratchpad and trigger decision guidance for the LLM.
 */
data class ToolObservation(
    val toolName: String,
    val result: String,
    val error: Boolean = false
)

/**
 * Accumulates agent loop iterations and renders them as a scratchpad
 * that is injected into the system message for subsequent LLM calls.
 *
 * When any iteration contains error observations, the scratchpad appends
 * a decision guidance block, prompting the LLM to adapt its strategy.
 */
class TaskMemory {
    private val _iterations = mutableListOf<AgentIteration>()
    val iterations: List<AgentIteration> get() = _iterations.toList()

    fun addIteration(iteration: AgentIteration) {
        _iterations.add(iteration)
    }

    /**
     * Renders all iterations as a human-readable scratchpad string.
     *
     * Returns `null` if no iterations have been recorded. Error observations
     * are prefixed with `ERROR` instead of `Observation`, and a decision
     * guidance section is appended when any errors are present.
     */
    fun toScratchpad(): String? {
        if (_iterations.isEmpty()) return null

        val hasErrors = _iterations.any { it.observations.any { obs -> obs.error } }

        val iterationText = _iterations.joinToString("\n\n") { iteration ->
            buildString {
                append("--- Iteration ${iteration.iterationNumber} ---")
                if (!iteration.thought.isNullOrBlank()) {
                    append("\nThought: ${iteration.thought}")
                }
                for (call in iteration.toolCalls) {
                    append("\nAction: ${call.toolName}(${call.arguments})")
                }
                for (obs in iteration.observations) {
                    if (obs.error) {
                        append("\nERROR [${obs.toolName}]: ${obs.result}")
                    } else {
                        append("\nObservation [${obs.toolName}]: ${obs.result}")
                    }
                }
            }
        }

        if (!hasErrors) return iterationText

        return iterationText + "\n\n## Decision guidance\n" +
            "One or more tool calls failed. Consider:\n" +
            "- Retrying with different arguments\n" +
            "- Trying a different tool\n" +
            "- Explaining the failure to the user"
    }
}
