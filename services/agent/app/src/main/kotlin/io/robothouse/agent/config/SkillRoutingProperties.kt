package io.robothouse.agent.config

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for skill routing.
 *
 * Controls the minimum embedding similarity score required for a skill match
 * and the minimum Jaccard similarity ratio for the fast keyword pre-filter.
 */
@Validated
@ConfigurationProperties(prefix = "skill.routing")
data class SkillRoutingProperties(
    @field:DecimalMin("0.0", message = "minSimilarityThreshold must be >= 0.0")
    @field:DecimalMax("1.0", message = "minSimilarityThreshold must be <= 1.0")
    val minSimilarityThreshold: Double,

    @field:DecimalMin("0.0", message = "minTokenOverlapRatio must be >= 0.0")
    @field:DecimalMax("1.0", message = "minTokenOverlapRatio must be <= 1.0")
    val minTokenOverlapRatio: Double
)
