package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for skill routing via embedding similarity.
 *
 * Controls context-aware routing behaviour and the similarity threshold
 * below which a fallback match triggers a context retry.
 */
@Validated
@ConfigurationProperties(prefix = "skill.routing")
data class SkillRoutingProperties(
    @field:Min(value = 0, message = "contextMessageCount must be >= 0")
    val contextMessageCount: Int = 2,

    @field:DecimalMin("0.0", message = "contextRetryThreshold must be >= 0.0")
    @field:DecimalMax("1.0", message = "contextRetryThreshold must be <= 1.0")
    val contextRetryThreshold: Double = 0.6
)
