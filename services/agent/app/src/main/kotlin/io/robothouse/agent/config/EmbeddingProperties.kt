package io.robothouse.agent.config

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for the embedding model and vector store.
 */
@Validated
@ConfigurationProperties(prefix = "embedding")
data class EmbeddingProperties(
    @field:Min(1, message = "dimension must be at least 1")
    val dimension: Int
)
