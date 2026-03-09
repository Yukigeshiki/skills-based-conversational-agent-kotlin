package io.robothouse.agent.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseValidationServiceTest {

    private fun fakeChatModel(response: String): ChatModel {
        return object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
    }

    @Test
    fun `returns true when response is adequate`() {
        val service = ResponseValidationService(fakeChatModel("ADEQUATE"))
        assertTrue(service.isAdequate("What is 2+2?", "2+2 equals 4."))
    }

    @Test
    fun `returns false when response is inadequate`() {
        val service = ResponseValidationService(fakeChatModel("INADEQUATE"))
        assertFalse(service.isAdequate("What is 2+2?", "I can't help with math questions."))
    }

    @Test
    fun `handles case-insensitive response`() {
        val service = ResponseValidationService(fakeChatModel("adequate"))
        assertTrue(service.isAdequate("question", "answer"))
    }

    @Test
    fun `handles response with whitespace`() {
        val service = ResponseValidationService(fakeChatModel("  INADEQUATE  "))
        assertFalse(service.isAdequate("question", "I don't know"))
    }

    @Test
    fun `defaults to adequate on error`() {
        val errorModel = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                throw RuntimeException("API error")
            }
        }
        val service = ResponseValidationService(errorModel)
        assertTrue(service.isAdequate("question", "answer"))
    }
}
