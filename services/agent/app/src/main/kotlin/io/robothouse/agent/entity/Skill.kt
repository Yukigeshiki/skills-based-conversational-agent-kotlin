package io.robothouse.agent.entity

import io.robothouse.agent.converter.StringListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a skill that the agent can be routed to.
 *
 * Each skill defines a system prompt, a set of available tools,
 * and an optional planning prompt for multi-step task decomposition.
 */
@Entity
@Table(name = "skills")
class Skill(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false)
    var name: String = "",

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    var description: String = "",

    @Column(name = "system_prompt", nullable = false, length = MAX_SYSTEM_PROMPT_LENGTH)
    var systemPrompt: String = "",

    @Column(name = "tool_names", nullable = false)
    @Convert(converter = StringListConverter::class)
    var toolNames: List<String> = emptyList(),

    @Column(name = "planning_prompt", length = MAX_SYSTEM_PROMPT_LENGTH)
    var planningPrompt: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PrePersist
    fun onPrePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onPreUpdate() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_SYSTEM_PROMPT_LENGTH = 4000
    }
}
