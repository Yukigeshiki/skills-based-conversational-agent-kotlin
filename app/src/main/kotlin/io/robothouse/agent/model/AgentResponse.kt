package io.robothouse.agent.model

data class AgentResponse(
    val response: String,
    val skill: String? = null,
    val steps: List<ToolExecutionStep> = emptyList(),
    val toolExecutionCount: Int = 0
)

data class ToolExecutionStep(
    val toolName: String,
    val arguments: String,
    val result: String
)
