package io.robothouse.agent.service

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.data.segment.TextSegment
import io.robothouse.agent.config.SkillRoutingProperties
import io.robothouse.agent.util.log
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SkillRouterService(
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val skillRepository: SkillRepository,
    private val properties: SkillRoutingProperties
) {

    fun route(userMessage: String): Skill {
        val queryEmbedding = embeddingModel.embed(userMessage).content()

        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .minScore(properties.minSimilarityScore)
            .build()

        val results = embeddingStore.search(searchRequest)

        results.matches().firstOrNull()?.let { match ->
            val skillId = match.embedded().metadata().getString("skillId")
            log.info { "Matched skill ID $skillId with score ${match.score()}" }

            skillRepository.findById(UUID.fromString(skillId)).orElse(null)?.let { skill ->
                log.info { "Routing to skill: ${skill.name}" }
                return skill
            }
        }

        log.info { "No match above threshold, falling back to ${properties.fallbackSkillName}" }
        return skillRepository.findByName(properties.fallbackSkillName)
            ?: throw IllegalStateException("Fallback skill '${properties.fallbackSkillName}' not found")
    }
}
