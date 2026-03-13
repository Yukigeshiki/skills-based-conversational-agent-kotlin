package io.robothouse.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for skill reference chunking and retrieval.
 */
@ConfigurationProperties(prefix = "skill.reference")
data class ReferenceProperties(
    val chunkTargetTokens: Int,
    val chunkOverlapTokens: Int,
    val retrievalMaxResults: Int,
    val retrievalMinScore: Double
)
