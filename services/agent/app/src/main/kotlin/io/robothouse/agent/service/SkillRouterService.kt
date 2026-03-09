package io.robothouse.agent.service

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
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
 * Always picks the highest-scoring skill. If that skill is the fallback and the
 * score is below [SkillRoutingProperties.contextRetryThreshold], retries with
 * recent user messages prepended so that terse follow-ups like "yes" inherit the
 * conversation's semantic context.
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
     * Routes to the highest-scoring skill. If the best match is the fallback skill
     * with a score below the context retry threshold and conversation history is
     * available, retries with context from recent user messages.
     */
    fun route(userMessage: String, conversationHistory: List<ConversationMessage> = emptyList()): Skill {
        log.debug { "Routing message to skill" }

        log.debug { "Pass 1 — direct match for query: \"$userMessage\"" }
        val directMatch = findTopSkill(userMessage)

        if (directMatch != null && directMatch.skill.name != FALLBACK_SKILL_NAME) {
            return directMatch.skill
        }

        val shouldRetryWithContext = directMatch != null
            && directMatch.skill.name == FALLBACK_SKILL_NAME
            && directMatch.score < properties.contextRetryThreshold
            && conversationHistory.isNotEmpty()
            && properties.contextMessageCount > 0

        if (shouldRetryWithContext) {
            val contextualQuery = buildContextualQuery(userMessage, conversationHistory)
            log.debug { "Pass 2 — fallback score ${directMatch!!.score} below threshold ${properties.contextRetryThreshold}, retrying with context:\n$contextualQuery" }
            val contextMatch = findTopSkill(contextualQuery)
            if (contextMatch != null) return contextMatch.skill
        }

        if (directMatch != null) return directMatch.skill

        log.info { "No embedding matches found, falling back to: ${FALLBACK_SKILL_NAME}" }
        return skillRepository.findByName(FALLBACK_SKILL_NAME)
            ?: run {
                log.warn { "Fallback skill not found: name=${FALLBACK_SKILL_NAME}" }
                throw NotFoundException("Fallback skill not found")
            }
    }

    private fun findTopSkill(query: String): ScoredSkill? {
        val queryEmbedding = embeddingModel.embed(query).content()

        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .build()

        val results = embeddingStore.search(searchRequest)
        val topMatch = results.matches().firstOrNull()

        if (topMatch == null) {
            log.debug { "No embedding matches found" }
            return null
        }

        log.debug { "Top embedding match — score: ${topMatch.score()}, segment: \"${topMatch.embedded()?.text()}\"" }

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

    private fun buildContextualQuery(userMessage: String, conversationHistory: List<ConversationMessage>): String {
        if (conversationHistory.isEmpty() || properties.contextMessageCount == 0) return userMessage

        val recentUserMessages = conversationHistory
            .filter { it.role == "user" }
            .takeLast(properties.contextMessageCount)

        if (recentUserMessages.isEmpty()) return userMessage

        val historyLines = recentUserMessages.joinToString("\n") { it.content }
        return "$historyLines\n$userMessage"
    }
}
