package io.robothouse.agent.service

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.util.log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Validates whether a skill's response adequately answers the user's question.
 *
 * Uses the light chat model to cheaply classify responses as adequate or not.
 * When a specialist skill produces an inadequate response (e.g. "I can't help
 * with that"), the caller can reroute to the general-assistant.
 */
@Service
class ResponseValidationService(
    @param:Qualifier("lightChatModel") private val chatModel: ChatModel
) {

    companion object {
        val VALIDATION_PROMPT = """
            |You are a strict response quality judge. Given a user question and an assistant response, decide if the response adequately answers the question.
            |
            |DEFAULT TO ADEQUATE. Only mark INADEQUATE when BOTH of these are true:
            |  1. The response contains NO substantive answer to the user's question, AND
            |  2. The response explicitly refuses, claims lack of knowledge or expertise,
            |     or tells the user to seek help elsewhere.
            |
            |If either of those conditions is missing, the response is ADEQUATE.
            |
            |The following are ADEQUATE and must NOT be flagged:
            |- Any response that contains a direct answer, even if imperfect or partial
            |- Hedging or qualifying language around a real answer ("I think...", "Actually...", "It seems...", "From what I checked...")
            |- A friendly closing offer like "Is there anything else I can help you with?" or "Let me know if you'd like more detail" — these are offering MORE help, not deflecting
            |- References to the assistant's own prior steps or tool calls ("From my previous check...", "Based on the lookup...")
            |- Brief or terse answers, as long as they answer the question
            |- Responses with formatting, emojis, or stylistic flourishes
            |
            |The following are INADEQUATE:
            |- "I cannot help with that" / "I'm not able to answer" / "That's outside my area of expertise" — with no answer attempted
            |- "I don't have knowledge about [topic]" — with no answer attempted
            |- "I'd suggest you try a different assistant/service for this" — with no answer attempted
            |- A response that is entirely about a different topic than the question
            |
            |Respond with ONLY the single word "ADEQUATE" or "INADEQUATE". No other text.
        """.trimMargin()
    }

    /**
     * Returns true if the response adequately answers the user's question.
     *
     * Defaults to true on any error to avoid blocking the response.
     */
    fun isAdequate(userMessage: String, response: String): Boolean {
        return try {
            val request = ChatRequest.builder()
                .messages(
                    listOf(
                        SystemMessage.from(VALIDATION_PROMPT),
                        UserMessage.from("User question: $userMessage\n\nAssistant response: $response")
                    )
                )
                .build()

            val result = chatModel.chat(request).aiMessage().text()?.trim()?.uppercase() ?: ""
            val adequate = result.startsWith("ADEQUATE")

            if (!adequate) {
                val preview = response.take(200).replace("\n", " ")
                log.info { "Response validation: INADEQUATE — will reroute to fallback. Response preview: \"$preview\"" }
            } else {
                log.debug { "Response validation: ADEQUATE" }
            }

            adequate
        } catch (e: Exception) {
            log.warn { "Response validation failed, defaulting to adequate: ${e.message}" }
            true
        }
    }
}
