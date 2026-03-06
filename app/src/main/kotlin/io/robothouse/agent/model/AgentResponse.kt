package io.robothouse.agent.model

/**
 * Response from the agent containing the final text response,
 * the matched skill, tool execution details, and optional task plan results.
 */
data class AgentResponse(
    val response: String,
    val skill: String? = null,
    val steps: List<ToolExecutionStep> = emptyList(),
    val toolExecutionCount: Int = 0,
    val plan: TaskPlan? = null,
    val planStepResults: List<PlanStepResult>? = null,
    val iterations: List<AgentIteration> = emptyList()
)

/**
 * Record of a single tool invocation including its arguments and result.
 */
data class ToolExecutionStep(
    val toolName: String,
    val arguments: String,
    val result: String
)

/**
 * Structured plan decomposing a user request into sequential steps,
 * produced by the task planning phase.
 */
data class TaskPlan(
    val steps: List<PlanStep>,
    val reasoning: String
)

/**
 * A single step within a task plan, describing what to do
 * and which tools are expected to be used.
 */
data class PlanStep(
    val stepNumber: Int,
    val description: String,
    val expectedTools: List<String> = emptyList()
)

/**
 * The outcome of executing a single plan step, capturing
 * its status, response text, and any tool executions performed.
 */
data class PlanStepResult(
    val step: PlanStep,
    val status: PlanStepStatus,
    val response: String,
    val toolSteps: List<ToolExecutionStep> = emptyList(),
    val iterations: List<AgentIteration> = emptyList()
)

/**
 * Execution status of a plan step.
 */
enum class PlanStepStatus {
    COMPLETED, FAILED, SKIPPED
}
