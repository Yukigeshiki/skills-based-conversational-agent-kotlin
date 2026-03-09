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
            |You are a response quality judge. Given a user question and an assistant response, determine if the response adequately answers the question.
            |
            |A response is INADEQUATE if it:
            |- Says it cannot help, doesn't know, or is outside its expertise
            |- Deflects the question without providing useful information
            |- Asks the user to try a different assistant or service
            |- Is completely off-topic or irrelevant to the question
            |
            |A response is ADEQUATE if it:
            |- Provides a direct answer or useful information related to the question
            |- Makes a reasonable attempt to address the question, even if imperfect
            |
            |Respond with ONLY the word "ADEQUATE" or "INADEQUATE".
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

            val result = chatModel.chat(request).aiMessage().text()?.trim()?.uppercase()
            val adequate = result == "ADEQUATE"

            if (!adequate) {
                log.info { "Response validation: INADEQUATE — will reroute to fallback" }
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
