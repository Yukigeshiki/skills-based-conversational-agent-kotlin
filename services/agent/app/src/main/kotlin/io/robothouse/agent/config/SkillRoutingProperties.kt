package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
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
    @field:DecimalMin("0.0", message = "contextRetryThreshold must be >= 0.0")
    @field:DecimalMax("1.0", message = "contextRetryThreshold must be <= 1.0")
    val contextRetryThreshold: Double = 0.6
)
