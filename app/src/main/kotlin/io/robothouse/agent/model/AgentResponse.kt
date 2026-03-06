package io.robothouse.agent.model

data class AgentResponse(
    val response: String,
    val skill: String? = null,
    val steps: List<ToolExecutionStep> = emptyList(),
    val toolExecutionCount: Int = 0,
    val plan: TaskPlan? = null,
    val planStepResults: List<PlanStepResult>? = null
)

data class ToolExecutionStep(
    val toolName: String,
    val arguments: String,
    val result: String
)

data class TaskPlan(
    val steps: List<PlanStep>,
    val reasoning: String
)

data class PlanStep(
    val stepNumber: Int,
    val description: String,
    val expectedTools: List<String> = emptyList()
)

data class PlanStepResult(
    val step: PlanStep,
    val status: PlanStepStatus,
    val response: String,
    val toolSteps: List<ToolExecutionStep> = emptyList()
)

enum class PlanStepStatus {
    COMPLETED, FAILED, SKIPPED
}
