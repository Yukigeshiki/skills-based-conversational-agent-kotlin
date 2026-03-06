package io.robothouse.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "skill.routing")
data class SkillRoutingProperties(
    val minSimilarityScore: Double = 0.5,
    val fallbackSkillName: String = "general-assistant"
)
