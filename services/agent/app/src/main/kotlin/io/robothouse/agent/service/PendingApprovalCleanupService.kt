package io.robothouse.agent.service

import io.robothouse.agent.model.ApprovalStatus
import io.robothouse.agent.repository.PendingApprovalRepository
import io.robothouse.agent.util.log
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Cleans up stale pending approval records that were never resolved.
 * Runs hourly and removes approvals older than 24 hours that are
 * still in PENDING status.
 */
@Service
class PendingApprovalCleanupService(
    private val pendingApprovalRepository: PendingApprovalRepository
) {

    companion object {
        private const val STALE_HOURS = 24L
    }

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    fun cleanupStaleApprovals() {
        val cutoff = Instant.now().minus(STALE_HOURS, ChronoUnit.HOURS)
        val deleted = pendingApprovalRepository.deleteByStatusAndCreatedAtBefore(ApprovalStatus.PENDING, cutoff)
        if (deleted > 0) {
            log.info { "Cleaned up $deleted stale pending approval(s) older than $STALE_HOURS hours" }
        }
    }
}
