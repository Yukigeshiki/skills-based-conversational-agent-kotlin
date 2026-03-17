package io.robothouse.agent.graph.checkpoint

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage

/**
 * Jackson module for serializing and deserializing Langchain4j's [ChatMessage]
 * hierarchy to JSON. Uses a `_type` discriminator to distinguish between
 * message subtypes during deserialization.
 */
class ChatMessageModule : SimpleModule() {

    init {
        addSerializer(ChatMessage::class.java, ChatMessageSerializer())
        addDeserializer(ChatMessage::class.java, ChatMessageDeserializer())
    }
}

/**
 * Serializes [ChatMessage] subtypes to JSON using a `_type` discriminator field.
 * Extracts data via Langchain4j's public API methods rather than accessing
 * internal fields directly.
 */
private class ChatMessageSerializer : JsonSerializer<ChatMessage>() {

    override fun serialize(msg: ChatMessage, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        when (msg) {
            is SystemMessage -> {
                gen.writeStringField("_type", "system")
                gen.writeStringField("text", msg.text())
            }
            is UserMessage -> {
                gen.writeStringField("_type", "user")
                gen.writeStringField("text", msg.singleText())
            }
            is AiMessage -> {
                gen.writeStringField("_type", "ai")
                gen.writeStringField("text", msg.text())
                if (msg.hasToolExecutionRequests()) {
                    gen.writeArrayFieldStart("toolExecutionRequests")
                    msg.toolExecutionRequests().forEach { req ->
                        gen.writeStartObject()
                        gen.writeStringField("id", req.id())
                        gen.writeStringField("name", req.name())
                        gen.writeStringField("arguments", req.arguments())
                        gen.writeEndObject()
                    }
                    gen.writeEndArray()
                }
            }
            is ToolExecutionResultMessage -> {
                gen.writeStringField("_type", "tool_result")
                gen.writeStringField("id", msg.id())
                gen.writeStringField("toolName", msg.toolName())
                gen.writeStringField("text", msg.text())
            }
            else -> throw IllegalArgumentException("Unknown ChatMessage type: ${msg::class}")
        }
        gen.writeEndObject()
    }
}

/**
 * Deserializes JSON back to [ChatMessage] subtypes by reading the `_type`
 * discriminator and reconstructing via Langchain4j's static factory methods.
 */
private class ChatMessageDeserializer : JsonDeserializer<ChatMessage>() {

    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): ChatMessage {
        val node = parser.codec.readTree<ObjectNode>(parser)
        val type = node.get("_type")?.asText()
            ?: throw IllegalArgumentException("ChatMessage JSON missing '_type' field")

        return when (type) {
            // System prompt message — plain text only
            "system" -> SystemMessage.from(node.get("text").asText())
            // User input message — plain text only
            "user" -> UserMessage.from(node.get("text").asText())
            // LLM response — may contain text, tool requests, or both
            "ai" -> {
                val text = node.get("text")?.takeIf { !it.isNull }?.asText()
                val toolRequests = node.get("toolExecutionRequests")?.map { reqNode ->
                    ToolExecutionRequest.builder()
                        .id(reqNode.get("id")?.asText())
                        .name(reqNode.get("name").asText())
                        .arguments(reqNode.get("arguments").asText())
                        .build()
                }
                if (toolRequests.isNullOrEmpty()) {
                    AiMessage.from(text ?: "")
                } else {
                    AiMessage(text, toolRequests)
                }
            }
            // Result returned from a tool execution
            "tool_result" -> ToolExecutionResultMessage.from(
                node.get("id").asText(),
                node.get("toolName").asText(),
                node.get("text").asText()
            )
            else -> throw IllegalArgumentException("Unknown ChatMessage _type: $type")
        }
    }
}
