package io.robothouse.agent.model

data class AgentIteration(
    val iterationNumber: Int,
    val thought: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val observations: List<ToolObservation> = emptyList()
)

data class ToolCall(
    val toolName: String,
    val arguments: String
)

data class ToolObservation(
    val toolName: String,
    val result: String
)

class TaskMemory {
    private val _iterations = mutableListOf<AgentIteration>()
    val iterations: List<AgentIteration> get() = _iterations.toList()

    fun addIteration(iteration: AgentIteration) {
        _iterations.add(iteration)
    }

    fun toScratchpad(): String? {
        if (_iterations.isEmpty()) return null

        return _iterations.joinToString("\n\n") { iteration ->
            buildString {
                append("--- Iteration ${iteration.iterationNumber} ---")
                if (!iteration.thought.isNullOrBlank()) {
                    append("\nThought: ${iteration.thought}")
                }
                for (call in iteration.toolCalls) {
                    append("\nAction: ${call.toolName}(${call.arguments})")
                }
                for (obs in iteration.observations) {
                    append("\nObservation [${obs.toolName}]: ${obs.result}")
                }
            }
        }
    }
}
