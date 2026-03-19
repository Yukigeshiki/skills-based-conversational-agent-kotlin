package io.robothouse.agent.service

import io.robothouse.agent.entity.PendingApproval
import io.robothouse.agent.exception.ConflictException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.ApprovalDecision
import io.robothouse.agent.model.ApprovalRequest
import io.robothouse.agent.model.ApprovalStatus
import io.robothouse.agent.model.PendingToolCall
import io.robothouse.agent.repository.PendingApprovalRepository
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Manages the lifecycle of pending tool approvals, handling lookup,
 * validation, and status transitions for human-in-the-loop decisions.
 */
@Service
class PendingApprovalService(
    private val pendingApprovalRepository: PendingApprovalRepository
) {

    /**
     * Creates a new pending approval record for tool calls awaiting
     * human review.
     */
    fun create(
        conversationId: String,
        threadId: String,
        skillName: String,
        toolCalls: List<PendingToolCall>
    ): PendingApproval {
        return pendingApprovalRepository.save(
            PendingApproval(
                conversationId = conversationId,
                threadId = threadId,
                skillName = skillName,
                toolCalls = toolCalls,
                status = ApprovalStatus.PENDING
            )
        )
    }

    /**
     * Resolves a pending approval by validating its status and updating
     * it to APPROVED or REJECTED. Throws [NotFoundException] if the
     * approval does not exist, or [ConflictException] if it has already
     * been resolved.
     */
    fun resolve(request: ApprovalRequest): PendingApproval {
        val approval = pendingApprovalRepository.findById(request.approvalId).orElseThrow {
            log.warn { "Pending approval not found: ${request.approvalId}" }
            NotFoundException("Pending approval not found: ${request.approvalId}")
        }

        if (approval.status != ApprovalStatus.PENDING) {
            log.warn { "Approval ${request.approvalId} has already been resolved: ${approval.status}" }
            throw ConflictException("Approval ${request.approvalId} has already been resolved: ${approval.status}")
        }

        approval.status = if (request.decision == ApprovalDecision.APPROVED) ApprovalStatus.APPROVED else ApprovalStatus.REJECTED
        approval.resolvedAt = Instant.now()
        return pendingApprovalRepository.save(approval)
    }
}
