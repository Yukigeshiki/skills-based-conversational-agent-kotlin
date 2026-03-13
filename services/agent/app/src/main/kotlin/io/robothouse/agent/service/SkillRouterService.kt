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
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Routes user messages to the most relevant skill using embedding similarity search.
 *
 * Picks the highest-scoring skill above [SkillRoutingProperties.minSimilarityThreshold].
 * If the fallback skill wins and conversation history is available, retries with recent
 * messages prepended so that terse follow-ups like "yes" inherit the conversation's
 * semantic context.
 */
@Service
class SkillRouterService(
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val skillRepository: SkillRepository,
    private val properties: SkillRoutingProperties
) {

    companion object {
        const val FALLBACK_SKILL_NAME = "general-assistant"
    }

    private data class ScoredSkill(val skill: Skill, val score: Double)

    /**
     * Finds the best-matching skill for a user message using embedding similarity.
     *
     * First checks if the user explicitly mentions a skill by name. If so, routes
     * directly to that skill. Otherwise, routes to the highest-scoring skill via
     * embedding similarity, requiring a minimum similarity score. If the fallback
     * skill wins and conversation history is available, retries with context from
     * recent messages.
     */
    fun route(userMessage: String, conversationHistory: List<ConversationMessage> = emptyList()): Skill {
        log.debug { "Routing message to skill" }

        findSkillByNameMention(userMessage)?.let { skill ->
            log.info { "Routed to skill by name mention: ${skill.name}" }
            return skill
        }

        log.debug { "Pass 1 — direct match for query: \"$userMessage\"" }
        val directMatch = findTopSkill(userMessage)

        if (directMatch != null && directMatch.skill.name != FALLBACK_SKILL_NAME) {
            return directMatch.skill
        }

        if ((directMatch == null || directMatch.skill.name == FALLBACK_SKILL_NAME) && conversationHistory.isNotEmpty()) {
            val contextualQuery = buildContextualQuery(userMessage, conversationHistory)
            log.debug { "Pass 2 — fallback matched, retrying with context:\n$contextualQuery" }
            val contextMatch = findTopSkill(contextualQuery)
            if (contextMatch != null) return contextMatch.skill
        }

        if (directMatch != null) return directMatch.skill

        log.info { "No embedding matches found, falling back to: $FALLBACK_SKILL_NAME" }
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
        return skillRepository.findAll()
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

    /**
     * Prepends the last assistant message from [conversationHistory] to [userMessage]
     * for context-aware embedding search.
     */
    private fun buildContextualQuery(userMessage: String, conversationHistory: List<ConversationMessage>): String {
        val lastAssistantMessage = conversationHistory
            .lastOrNull { it.role == "assistant" }
            ?: return userMessage

        return "Follow-up in an ongoing conversation.\n" +
            "Previous assistant response: ${lastAssistantMessage.content}\n" +
            "Current message: $userMessage"
    }
}
