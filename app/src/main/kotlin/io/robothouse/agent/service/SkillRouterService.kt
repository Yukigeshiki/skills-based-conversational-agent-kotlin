package io.robothouse.agent.service

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.data.segment.TextSegment
import io.robothouse.agent.config.SkillRoutingProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.repository.SkillRepository
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Routes user messages to the most relevant skill using embedding similarity search.
 *
 * Falls back to a configurable default skill when no match
 * exceeds the minimum similarity threshold.
 */
@Service
class SkillRouterService(
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val skillRepository: SkillRepository,
    private val properties: SkillRoutingProperties
) {

    /**
     * Finds the best-matching skill for a user message using embedding similarity.
     *
     * Returns the fallback skill if no match exceeds the minimum similarity threshold.
     */
    fun route(userMessage: String): Skill {
        log.debug { "Routing message to skill" }

        val queryEmbedding = embeddingModel.embed(userMessage).content()

        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .minScore(properties.minSimilarityScore)
            .build()

        val results = embeddingStore.search(searchRequest)

        val matchedSkill = results.matches().firstOrNull()
            ?.let { match ->
                val skillIdStr = match.embedded()?.metadata()?.getString("skillId") ?: return@let null
                val skillId = runCatching { UUID.fromString(skillIdStr) }
                    .onFailure { log.warn { "Invalid skillId in embedding metadata: $skillIdStr" } }
                    .getOrNull() ?: return@let null
                log.debug { "Matched skill ID: $skillId with score: ${match.score()}" }
                skillRepository.findById(skillId).orElse(null)?.also { skill ->
                    log.info { "Routed to skill: ${skill.name} (score: ${match.score()})" }
                }
            }

        if (matchedSkill != null) return matchedSkill

        log.info { "No match above threshold, falling back to: ${properties.fallbackSkillName}" }
        return skillRepository.findByName(properties.fallbackSkillName)
            ?: run {
                log.warn { "Fallback skill not found: name=${properties.fallbackSkillName}" }
                throw NotFoundException("Fallback skill not found")
            }
    }
}
