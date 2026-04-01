package io.robothouse.agent.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA entity representing the singleton identity configuration that provides
 * a global personality prepended to every skill's system prompt.
 */
@Entity
@Table(name = "identity")
class Identity(
    @Id
    var id: Int = 1,

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    var systemPrompt: String = "",

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
}
