package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for skill routing via embedding similarity.
 *
 * Controls the minimum similarity score threshold for matching
 * and the fallback skill used when no match exceeds the threshold.
 */
@Validated
@ConfigurationProperties(prefix = "skill.routing")
data class SkillRoutingProperties(
    @field:DecimalMin("0.0", message = "minSimilarityScore must be >= 0.0")
    @field:DecimalMax("1.0", message = "minSimilarityScore must be <= 1.0")
    val minSimilarityScore: Double,

    @field:NotBlank(message = "fallbackSkillName must not be blank")
    val fallbackSkillName: String = "general-assistant"
)
