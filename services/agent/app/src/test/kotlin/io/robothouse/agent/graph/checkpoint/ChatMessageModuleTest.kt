package io.robothouse.agent.graph.checkpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatMessageModuleTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper().registerModule(ChatMessageModule())
    }

    @Test
    fun `round-trips SystemMessage`() {
        val msg: ChatMessage = SystemMessage.from("You are a helpful assistant.")
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is SystemMessage)
        assertEquals("You are a helpful assistant.", (deserialized as SystemMessage).text())
    }

    @Test
    fun `round-trips UserMessage`() {
        val msg: ChatMessage = UserMessage.from("What time is it?")
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is UserMessage)
        assertEquals("What time is it?", (deserialized as UserMessage).singleText())
    }

    @Test
    fun `round-trips AiMessage with text only`() {
        val msg: ChatMessage = AiMessage.from("It is 3pm.")
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is AiMessage)
        assertEquals("It is 3pm.", (deserialized as AiMessage).text())
        assertTrue(!deserialized.hasToolExecutionRequests())
    }

    @Test
    fun `round-trips AiMessage with tool execution requests`() {
        val toolRequest = ToolExecutionRequest.builder()
            .id("call_123")
            .name("getCurrentDateTime")
            .arguments("{\"timezone\": \"UTC\"}")
            .build()
        val msg: ChatMessage = AiMessage("Let me check", listOf(toolRequest))
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is AiMessage)
        val ai = deserialized as AiMessage
        assertEquals("Let me check", ai.text())
        assertTrue(ai.hasToolExecutionRequests())
        assertEquals(1, ai.toolExecutionRequests().size)
        assertEquals("call_123", ai.toolExecutionRequests()[0].id())
        assertEquals("getCurrentDateTime", ai.toolExecutionRequests()[0].name())
        assertEquals("{\"timezone\": \"UTC\"}", ai.toolExecutionRequests()[0].arguments())
    }

    @Test
    fun `round-trips AiMessage with tool requests and null text`() {
        val toolRequest = ToolExecutionRequest.builder()
            .id("call_456")
            .name("myTool")
            .arguments("{}")
            .build()
        val msg: ChatMessage = AiMessage(null, listOf(toolRequest))
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is AiMessage)
        val ai = deserialized as AiMessage
        assertNull(ai.text())
        assertTrue(ai.hasToolExecutionRequests())
        assertEquals("myTool", ai.toolExecutionRequests()[0].name())
    }

    @Test
    fun `round-trips ToolExecutionResultMessage`() {
        val msg: ChatMessage = ToolExecutionResultMessage.from("call_123", "getCurrentDateTime", "2026-03-17 10:00 UTC")
        val json = objectMapper.writeValueAsString(msg)
        val deserialized = objectMapper.readValue(json, ChatMessage::class.java)

        assertTrue(deserialized is ToolExecutionResultMessage)
        val result = deserialized as ToolExecutionResultMessage
        assertEquals("call_123", result.id())
        assertEquals("getCurrentDateTime", result.toolName())
        assertEquals("2026-03-17 10:00 UTC", result.text())
    }

    @Test
    fun `serializes list of mixed ChatMessage types`() {
        val messages: List<ChatMessage> = listOf(
            SystemMessage.from("System prompt"),
            UserMessage.from("Hello"),
            AiMessage.from("Hi!"),
            UserMessage.from("Use a tool"),
            AiMessage(null, listOf(
                ToolExecutionRequest.builder().id("c1").name("tool1").arguments("{}").build()
            )),
            ToolExecutionResultMessage.from("c1", "tool1", "result"),
            AiMessage.from("Done.")
        )

        val json = objectMapper.writeValueAsString(messages)
        val typeRef = objectMapper.typeFactory.constructCollectionType(List::class.java, ChatMessage::class.java)
        val deserialized: List<ChatMessage> = objectMapper.readValue(json, typeRef)

        assertEquals(7, deserialized.size)
        assertTrue(deserialized[0] is SystemMessage)
        assertTrue(deserialized[1] is UserMessage)
        assertTrue(deserialized[2] is AiMessage)
        assertTrue(deserialized[3] is UserMessage)
        assertTrue(deserialized[4] is AiMessage)
        assertTrue((deserialized[4] as AiMessage).hasToolExecutionRequests())
        assertTrue(deserialized[5] is ToolExecutionResultMessage)
        assertTrue(deserialized[6] is AiMessage)
    }
}
