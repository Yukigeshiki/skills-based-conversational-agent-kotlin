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
 * Routes user messages to the most relevant skill using a four-stage cascade:
 * name mention, token overlap, embedding similarity, and fallback.
 *
 * Name mention and token overlap are fast in-memory checks against the skill cache.
 * The embedding path enriches the query via [QueryEnrichmentService] and searches
 * pgvector for the closest match above [SkillRoutingProperties.minSimilarityThreshold].
 * If no stage produces a match, the general-assistant fallback handles the request.
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

        private val WORD_BOUNDARY = Regex("\\W+")
        private const val MIN_TOKEN_LENGTH = 3

        /**
         * Common English stop words filtered out before token overlap scoring.
         * Removing these prevents inflated scores from words that appear in nearly
         * every skill description but carry no domain signal.
         */
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "from", "into", "this", "that", "these", "those",
            "are", "was", "were", "been", "being", "has", "have", "had", "will", "would",
            "could", "should", "can", "may", "might", "must", "shall",
            "you", "your", "yours", "yourself", "yourselves",
            "his", "her", "hers", "him", "she", "them", "they", "their", "theirs", "our", "ours",
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "all", "any", "both", "each", "few", "more", "most", "other", "some", "such",
            "than", "too", "very", "just", "only", "also", "yet", "but", "not", "nor",
            "about", "above", "after", "again", "against", "before", "below", "between",
            "during", "over", "under", "until", "while", "down", "off", "out", "through",
            "tell", "show", "give", "make", "use", "get", "want", "need", "like", "know",
            "please", "thanks", "thank", "hello", "hey",
        )
    }

    private data class ScoredSkill(val skill: Skill, val score: Double)

    /**
     * Finds the best-matching skill for a user message using a four-stage cascade:
     * name mention, token overlap, query enrichment with embedding similarity, and
     * fallback. The first two stages are fast in-memory checks; the embedding stage
     * involves an LLM enrichment call and a pgvector search.
     */
    fun route(userMessage: String, conversationHistory: List<ConversationMessage> = emptyList()): Skill {
        log.debug { "Routing message to skill" }

        findSkillByNameMention(userMessage)?.let { skill ->
            log.info { "Routed to skill by name mention: ${skill.name}" }
            return skill
        }

        findSkillByTokenOverlap(userMessage)?.let { match ->
            return match.skill
        }

        val query = if (conversationHistory.isNotEmpty()) {
            queryEnrichmentService.enrich(userMessage, conversationHistory)
        } else {
            userMessage
        }

        val match = findTopSkillByEmbedding(query)

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
     * Scores each skill by Jaccard similarity between the user message tokens
     * and the skill's name and description tokens, after stop word and length
     * filtering. Returns the highest-scoring skill above the configured ratio
     * threshold, or null otherwise. Ties are broken by skill name ascending so
     * routing is deterministic.
     */
    private fun findSkillByTokenOverlap(userMessage: String): ScoredSkill? {
        val userTokens = tokenize(userMessage)
        if (userTokens.isEmpty()) return null

        val match = skillCacheService.findAll()
            .asSequence()
            .filter { it.name != FALLBACK_SKILL_NAME }
            .map { skill ->
                val skillTokens = tokenize("${skill.name} ${skill.description}")
                ScoredSkill(skill, jaccard(userTokens, skillTokens))
            }
            .filter { it.score >= properties.minTokenOverlapRatio }
            .sortedWith(compareByDescending<ScoredSkill> { it.score }.thenBy { it.skill.name })
            .firstOrNull()

        return match?.also {
            log.info { "Routed to skill by token overlap: ${it.skill.name} (score: ${"%.2f".format(it.score)})" }
        }
    }

    /**
     * Splits text on word boundaries, lowercases, drops tokens shorter than
     * [MIN_TOKEN_LENGTH] characters, removes stop words, and returns the
     * resulting set of distinct tokens.
     */
    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(WORD_BOUNDARY)
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }
            .toSet()

    /**
     * Computes the Jaccard similarity between two token sets: the size of
     * the intersection divided by the size of the union. Returns 0.0 when
     * either set is empty or the union is empty.
     */
    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.size + b.size - intersection
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
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
    private fun findTopSkillByEmbedding(query: String): ScoredSkill? {
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
