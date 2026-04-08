package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for retrying transient LLM failures with jittered
 * exponential backoff. Applied uniformly to the agent and light chat models
 * by [ChatModelConfig].
 *
 * `attemptTimeoutMs` is a defensive upper bound on a single streaming attempt
 * — if the underlying client never invokes any callback, the streaming retry
 * decorator gives up and treats it as a transport error rather than hanging.
 */
@Validated
@ConfigurationProperties(prefix = "agent.models.retry")
data class LlmRetryProperties(
    @field:Min(value = 1, message = "maxAttempts must be >= 1")
    val maxAttempts: Int,

    @field:Min(value = 1, message = "initialDelayMs must be >= 1")
    val initialDelayMs: Long,

    @field:Min(value = 1, message = "maxDelayMs must be >= 1")
    val maxDelayMs: Long,

    @field:DecimalMin(value = "1.0", inclusive = false, message = "delayFactor must be > 1.0")
    val delayFactor: Double,

    @field:DecimalMin(value = "0.0", message = "jitterFactor must be >= 0.0")
    @field:DecimalMax(value = "1.0", message = "jitterFactor must be <= 1.0")
    val jitterFactor: Double,

    @field:Min(value = 1, message = "attemptTimeoutMs must be >= 1")
    val attemptTimeoutMs: Long
)
