package io.robothouse.agent.model

import java.util.UUID

/**
 * A tool call that is pending human approval before execution.
 */
data class PendingToolCall(
    val toolName: String,
    val arguments: String
)

/**
 * Lifecycle status of a pending tool approval record.
 */
enum class ApprovalStatus {
    PENDING, APPROVED, REJECTED
}

/**
 * Human decision on a pending tool approval.
 */
enum class ApprovalDecision {
    APPROVED, REJECTED
}

/**
 * Request body for the tool approval endpoint.
 */
data class ApprovalRequest(
    val approvalId: UUID,
    val decision: ApprovalDecision
)
