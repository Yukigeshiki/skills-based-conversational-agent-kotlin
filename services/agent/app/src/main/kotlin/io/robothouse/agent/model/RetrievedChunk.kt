package io.robothouse.agent.model

/**
 * A chunk of reference content retrieved via RAG similarity search.
 */
data class RetrievedChunk(
    val referenceId: String,
    val referenceName: String,
    val chunkIndex: Int,
    val content: String,
    val score: Double
)
