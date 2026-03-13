package io.robothouse.agent.service

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import io.robothouse.agent.config.ReferenceProperties
import io.robothouse.agent.model.RetrievedChunk
import io.robothouse.agent.repository.SkillReferenceRepository
import io.robothouse.agent.util.log
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Retrieves relevant reference chunks for a skill based on user message similarity.
 *
 * Searches the embedding store for reference-type embeddings scoped to the given
 * skill, returning the top matches above a minimum score threshold.
 */
@Service
@EnableConfigurationProperties(ReferenceProperties::class)
class ReferenceRetrievalService(
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val referenceProperties: ReferenceProperties,
    private val skillReferenceRepository: SkillReferenceRepository
) {

    /**
     * Retrieves the most relevant reference chunks for the given skill and user message.
     * Returns chunks sorted by relevance, or an empty list if none exceed the minimum
     * score threshold.
     */
    fun retrieveChunks(skillId: UUID, userMessage: String): List<RetrievedChunk> {
        if (!skillReferenceRepository.existsBySkillId(skillId)) {
            log.debug { "No references found for skill: id=$skillId, skipping retrieval" }
            return emptyList()
        }

        val queryEmbedding = embeddingModel.embed(userMessage).content()

        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(referenceProperties.retrievalMaxResults)
            .minScore(referenceProperties.retrievalMinScore)
            .filter(
                metadataKey("skillId").isEqualTo(skillId.toString())
                    .and(metadataKey("type").isEqualTo("reference"))
            )
            .build()

        val results = embeddingStore.search(searchRequest)
        val chunks = results.matches().mapNotNull { match ->
            val metadata = match.embedded()?.metadata() ?: return@mapNotNull null
            val referenceId = metadata.getString("referenceId") ?: return@mapNotNull null
            val chunkIndex = metadata.getInteger("chunkIndex") ?: return@mapNotNull null
            val content = match.embedded()?.text() ?: return@mapNotNull null

            RetrievedChunk(
                referenceId = referenceId,
                referenceName = "",
                chunkIndex = chunkIndex,
                content = content,
                score = match.score()
            )
        }

        val referenceIds = chunks.map { UUID.fromString(it.referenceId) }.distinct()
        val namesByReferenceId = skillReferenceRepository.findAllById(referenceIds)
            .associate { it.id.toString() to it.name }

        val resolvedChunks = chunks.map { chunk ->
            chunk.copy(referenceName = namesByReferenceId[chunk.referenceId] ?: "unknown")
        }

        log.debug { "Retrieved ${resolvedChunks.size} reference chunk(s) for skill: id=$skillId" }
        return resolvedChunks
    }
}
