package io.robothouse.agent.repository

import io.robothouse.agent.entity.PendingApproval
import io.robothouse.agent.model.ApprovalStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

/**
 * Repository for managing pending tool approval records.
 */
interface PendingApprovalRepository : JpaRepository<PendingApproval, UUID> {

    @Modifying
    @Query("DELETE FROM PendingApproval p WHERE p.status = :status AND p.createdAt < :cutoff")
    fun deleteByStatusAndCreatedAtBefore(status: ApprovalStatus, cutoff: Instant): Int
}
