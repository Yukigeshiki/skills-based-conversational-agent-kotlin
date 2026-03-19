package io.robothouse.agent.entity

import io.robothouse.agent.converter.PendingToolCallListConverter
import io.robothouse.agent.model.ApprovalStatus
import io.robothouse.agent.model.PendingToolCall
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a tool execution awaiting human approval.
 *
 * Created when a skill with `requiresApproval = true` triggers tool
 * calls and the agent graph interrupts before execution. Stores the
 * pending tool calls and the checkpoint thread ID needed to resume
 * the graph after approval.
 */
@Entity
@Table(name = "pending_approvals")
class PendingApproval(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(name = "conversation_id", nullable = false)
    var conversationId: String = "",

    @Column(name = "thread_id", nullable = false)
    var threadId: String = "",

    @Column(name = "skill_name", nullable = false)
    var skillName: String = "",

    @Column(name = "tool_calls", nullable = false, columnDefinition = "jsonb")
    @Convert(converter = PendingToolCallListConverter::class)
    var toolCalls: List<PendingToolCall> = emptyList(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ApprovalStatus = ApprovalStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
)
