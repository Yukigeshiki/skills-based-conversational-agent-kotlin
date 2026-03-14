package io.robothouse.agent.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.model.ConversationMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryEnrichmentServiceTest {

    private var capturedRequest: ChatRequest? = null

    private fun fakeChatModel(response: String?): ChatModel {
        return object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                capturedRequest = request
                if (response == null) {
                    return ChatResponse.builder().aiMessage(AiMessage.from("")).build()
                }
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
    }

    private fun errorChatModel(): ChatModel {
        return object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                throw RuntimeException("API error")
            }
        }
    }

    @Test
    fun `extracts Query line from structured response`() {
        val response = """
            Subject: gardening/horticulture
            Intent: learn about growing tomatoes
            Query: How do I grow tomatoes in a home garden?
        """.trimIndent()

        val service = QueryEnrichmentService(fakeChatModel(response))
        val result = service.enrich("growing tomatoes")

        assertEquals("How do I grow tomatoes in a home garden?", result)
    }

    @Test
    fun `falls back to full response when Query line is missing`() {
        val response = "How do I grow tomatoes in a home garden?"

        val service = QueryEnrichmentService(fakeChatModel(response))
        val result = service.enrich("growing tomatoes")

        assertEquals("How do I grow tomatoes in a home garden?", result)
    }

    @Test
    fun `returns raw message on LLM error`() {
        val service = QueryEnrichmentService(errorChatModel())
        val result = service.enrich("growing tomatoes")

        assertEquals("growing tomatoes", result)
    }

    @Test
    fun `returns raw message when LLM returns empty`() {
        val service = QueryEnrichmentService(fakeChatModel(null))
        val result = service.enrich("growing tomatoes")

        assertEquals("growing tomatoes", result)
    }

    @Test
    fun `includes recent history in user content`() {
        val service = QueryEnrichmentService(fakeChatModel("Query: enriched"))
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM.")
        )

        service.enrich("thanks", history)

        val userContent = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertTrue(userContent.contains("Conversation history:"))
        assertTrue(userContent.contains("user: What time is it?"))
        assertTrue(userContent.contains("assistant: It is 3:00 PM."))
        assertTrue(userContent.contains("Current message: thanks"))
    }

    @Test
    fun `limits history to last 4 messages`() {
        val service = QueryEnrichmentService(fakeChatModel("Query: enriched"))
        val history = listOf(
            ConversationMessage(role = "user", content = "msg1"),
            ConversationMessage(role = "assistant", content = "resp1"),
            ConversationMessage(role = "user", content = "msg2"),
            ConversationMessage(role = "assistant", content = "resp2"),
            ConversationMessage(role = "user", content = "msg3"),
            ConversationMessage(role = "assistant", content = "resp3")
        )

        service.enrich("msg4", history)

        val userContent = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertFalse(userContent.contains("msg1"), "Should not contain oldest message")
        assertFalse(userContent.contains("resp1"), "Should not contain oldest response")
        assertTrue(userContent.contains("msg2"))
        assertTrue(userContent.contains("resp3"))
    }

    @Test
    fun `enriches query even without history`() {
        val service = QueryEnrichmentService(fakeChatModel("Query: enriched growing tomatoes query"))

        val result = service.enrich("growing tomatoes")

        assertEquals("enriched growing tomatoes query", result)
        // Verify LLM was called (capturedRequest is set)
        assertTrue(capturedRequest != null, "LLM should be called even without history")
    }

    @Test
    fun `formats user content without history prefix when no history`() {
        val service = QueryEnrichmentService(fakeChatModel("Query: enriched"))

        service.enrich("growing tomatoes", emptyList())

        val userContent = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertEquals("Current message: growing tomatoes", userContent)
        assertFalse(userContent.contains("Conversation history:"))
    }
}
