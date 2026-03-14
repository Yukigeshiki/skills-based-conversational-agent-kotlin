package io.robothouse.agent.service

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.util.log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Enriches user queries with semantic context before embedding-based skill routing.
 *
 * Uses the light chat model to expand terse or ambiguous messages into self-contained
 * search queries with domain context, improving embedding similarity accuracy.
 */
@Service
class QueryEnrichmentService(
    @param:Qualifier("lightChatModel") private val chatModel: ChatModel
) {

    companion object {
        /** System prompt instructing the light model to reformulate queries for semantic search. */
        val ENRICHMENT_PROMPT = """
            |You enrich user queries for semantic search. Given a user message and optional conversation history,
            |output a self-contained search query that captures the full meaning.
            |
            |Rules:
            |- Add the relevant subject area or domain to the query
            |- Resolve pronouns and references using conversation history
            |- Expand terse follow-ups ("yes", "do it", "what about X?") into complete queries
            |- If the message is already clear, still add the subject/domain context
            |- Do NOT answer the question — only reformulate it
            |
            |Respond with ONLY this format, no other text:
            |Subject: <topic or domain>
            |Intent: <what the user wants to do>
            |Query: <complete, self-contained search query>
        """.trimMargin()
    }

    /**
     * Enriches the user message with semantic context for improved embedding search.
     *
     * Calls the light chat model with [ENRICHMENT_PROMPT] to produce a structured
     * response containing Subject, Intent, and Query lines. The `Query:` value is
     * extracted and returned. Falls back to the raw [userMessage] on any failure
     * (LLM error, empty response, missing Query line) to avoid blocking routing.
     */
    fun enrich(userMessage: String, conversationHistory: List<ConversationMessage> = emptyList()): String {
        return try {
            val userContent = buildUserContent(userMessage, conversationHistory)

            val request = ChatRequest.builder()
                .messages(
                    listOf(
                        SystemMessage.from(ENRICHMENT_PROMPT),
                        UserMessage.from(userContent)
                    )
                )
                .build()

            val response = chatModel.chat(request).aiMessage().text()
            val enriched = parseEnrichedResponse(response, userMessage)

            log.debug { "Enriched query: $enriched" }
            enriched
        } catch (e: Exception) {
            log.warn { "Query enrichment failed, using raw message: ${e.message}" }
            userMessage
        }
    }

    /**
     * Builds the user content string sent to the light model.
     *
     * When [conversationHistory] is present, includes the last 4 messages
     * formatted as `role: content` to provide context for pronoun resolution
     * and terse follow-up expansion. Otherwise, sends only the current message.
     */
    private fun buildUserContent(userMessage: String, conversationHistory: List<ConversationMessage>): String {
        if (conversationHistory.isEmpty()) {
            return "Current message: $userMessage"
        }

        val recentHistory = conversationHistory.takeLast(4)
            .joinToString("\n") { "${it.role}: ${it.content}" }

        return "Conversation history:\n$recentHistory\n\nCurrent message: $userMessage"
    }

    /**
     * Extracts the `Query:` line value from the structured LLM response.
     *
     * Falls back to the full response text if the `Query:` line is missing,
     * or to the raw [userMessage] if the response is null or blank.
     */
    private fun parseEnrichedResponse(response: String?, userMessage: String): String {
        if (response.isNullOrBlank()) return userMessage

        val queryLine = response.lines()
            .find { it.trim().startsWith("Query:") }
            ?.substringAfter("Query:")
            ?.trim()

        if (!queryLine.isNullOrBlank()) return queryLine

        return response.trim()
    }
}
