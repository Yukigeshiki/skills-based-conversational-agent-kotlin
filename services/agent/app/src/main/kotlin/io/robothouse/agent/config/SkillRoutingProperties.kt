package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for skill routing via embedding similarity.
 *
 * Controls the minimum similarity score required for a skill match.
 * When the fallback skill wins the match and conversation history is
 * available, a context retry is attempted automatically.
 */
@Validated
@ConfigurationProperties(prefix = "skill.routing")
data class SkillRoutingProperties(
    @field:DecimalMin("0.0", message = "minSimilarityThreshold must be >= 0.0")
    @field:DecimalMax("1.0", message = "minSimilarityThreshold must be <= 1.0")
    val minSimilarityThreshold: Double
)
