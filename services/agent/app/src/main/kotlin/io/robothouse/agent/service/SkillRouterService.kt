package io.robothouse.agent.service

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import dev.langchain4j.data.segment.TextSegment
import io.robothouse.agent.config.SkillRoutingProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.repository.SkillRepository
import io.robothouse.agent.util.SkillSeeder
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Routes user messages to the most relevant skill using embedding similarity search.
 *
 * Before embedding, enriches the user query via [QueryEnrichmentService] to add domain
 * context and resolve ambiguous references. Picks the highest-scoring skill above
 * [SkillRoutingProperties.minSimilarityThreshold], falling back to the general-assistant.
 */
@Service
class SkillRouterService(
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val skillRepository: SkillRepository,
    private val skillCacheService: SkillCacheService,
    private val properties: SkillRoutingProperties,
    private val queryEnrichmentService: QueryEnrichmentService
) {

    companion object {
        const val FALLBACK_SKILL_NAME = SkillSeeder.FALLBACK_SKILL_NAME
    }

    private data class ScoredSkill(val skill: Skill, val score: Double)

    /**
     * Finds the best-matching skill for a user message using embedding similarity.
     *
     * First checks if the user explicitly mentions a skill by name. If so, routes
     * directly to that skill. When conversation history exists, enriches the query
     * with domain context via [QueryEnrichmentService] to resolve terse follow-ups.
     * Routes to the highest-scoring skill via a single embedding similarity pass.
     */
    fun route(userMessage: String, conversationHistory: List<ConversationMessage> = emptyList()): Skill {
        log.debug { "Routing message to skill" }

        findSkillByNameMention(userMessage)?.let { skill ->
            log.info { "Routed to skill by name mention: ${skill.name}" }
            return skill
        }

        log.debug { "Routing query: \"$userMessage\"" }
        val query = if (conversationHistory.isNotEmpty()) {
            queryEnrichmentService.enrich(userMessage, conversationHistory)
        } else {
            userMessage
        }

        val match = findTopSkill(query)

        if (match != null && match.skill.name != FALLBACK_SKILL_NAME) {
            return match.skill
        }

        log.info { "No specialist match found, falling back to: $FALLBACK_SKILL_NAME" }
        return skillRepository.findByName(FALLBACK_SKILL_NAME)
            ?: run {
                log.warn { "Fallback skill not found: name=${FALLBACK_SKILL_NAME}" }
                throw NotFoundException("Fallback skill not found")
            }
    }

    /**
     * Returns the fallback skill, or throws [NotFoundException] if it does not exist.
     */
    fun findFallbackSkill(): Skill {
        return skillRepository.findByName(FALLBACK_SKILL_NAME)
            ?: run {
                log.warn { "Fallback skill not found: name=${FALLBACK_SKILL_NAME}" }
                throw NotFoundException("Fallback skill not found")
            }
    }

    /**
     * Checks if the user message mentions a known skill name (case-insensitive).
     * Excludes the fallback skill to avoid false positives on generic terms.
     */
    private fun findSkillByNameMention(userMessage: String): Skill? {
        val normalizedMessage = userMessage.normalize()
        return skillCacheService.findAll()
            .filter { it.name != FALLBACK_SKILL_NAME }
            .find { normalizedMessage.contains(it.name.normalize()) }
    }

    /**
     * Lowercases and strips hyphens/underscores so e.g. "datetime-assistant"
     * matches "datetime assistant".
     */
    private fun String.normalize(): String = lowercase().replace(Regex("[-_]"), "")

    /**
     * Embeds the [query] and returns the highest-scoring skill from the
     * embedding store, or null if none match.
     */
    private fun findTopSkill(query: String): ScoredSkill? {
        val queryEmbedding = embeddingModel.embed(query).content()

        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .filter(metadataKey("type").isEqualTo("skill"))
            .build()

        val results = embeddingStore.search(searchRequest)
        val topMatch = results.matches().firstOrNull()

        if (topMatch == null) {
            log.debug { "No embedding matches found" }
            return null
        }

        log.debug { "Top embedding match — score: ${topMatch.score()}, segment: \"${topMatch.embedded()?.text()}\"" }

        if (topMatch.score() < properties.minSimilarityThreshold) {
            log.debug { "Top match score ${topMatch.score()} below minimum threshold ${properties.minSimilarityThreshold}" }
            return null
        }

        val skill = topMatch.let { match ->
            val skillIdStr = match.embedded()?.metadata()?.getString("skillId") ?: return@let null
            val skillId = runCatching { UUID.fromString(skillIdStr) }
                .onFailure { log.warn { "Invalid skillId in embedding metadata: $skillIdStr" } }
                .getOrNull() ?: return@let null
            skillRepository.findById(skillId).orElse(null)?.also { s ->
                log.info { "Routed to skill: ${s.name} (score: ${match.score()})" }
            }
        } ?: return null

        return ScoredSkill(skill, topMatch.score())
    }
}
