package io.robothouse.agent.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for Redis-backed conversation memory.
 *
 * Controls the maximum number of messages retained per conversation
 * and the time-to-live before conversations expire from Redis.
 */
@Validated
@ConfigurationProperties(prefix = "conversation.memory")
data class ConversationMemoryProperties(
    @field:Min(1, message = "maxMessages must be at least 1")
    val maxMessages: Int,

    @field:Min(1, message = "ttlHours must be at least 1")
    val ttlHours: Long
)
