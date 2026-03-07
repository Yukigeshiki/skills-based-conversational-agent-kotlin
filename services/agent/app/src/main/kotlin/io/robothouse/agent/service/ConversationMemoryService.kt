package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.robothouse.agent.config.ConversationMemoryProperties
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.util.log
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Manages conversation history in Redis for multi-turn agent interactions.
 *
 * Messages are stored as JSON-serialized entries in a Redis list keyed by
 * conversation ID, with a configurable TTL refreshed on each write. Redis
 * failures are propagated to the caller so they can be surfaced appropriately.
 */
@Service
@EnableConfigurationProperties(ConversationMemoryProperties::class)
class ConversationMemoryService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: ConversationMemoryProperties
) {

    /**
     * Appends a message to the conversation history.
     *
     * Serializes the message as JSON, pushes it to the end of the Redis list,
     * and refreshes the TTL for the conversation key.
     */
    fun addMessage(conversationId: String, message: ConversationMessage) {
        val key = keyFor(conversationId)
        val json = objectMapper.writeValueAsString(message)
        redisTemplate.opsForList().rightPush(key, json)
        redisTemplate.opsForList().trim(key, -properties.maxMessages.toLong(), -1)
        redisTemplate.expire(key, Duration.ofHours(properties.ttlHours))
    }

    /**
     * Retrieves the conversation history for the given conversation ID.
     *
     * Reads the most recent entries (up to [ConversationMemoryProperties.maxMessages])
     * from the Redis list and deserializes them. Malformed entries are logged and skipped.
     */
    fun getHistory(conversationId: String): List<ConversationMessage> {
        val key = keyFor(conversationId)
        val entries = redisTemplate.opsForList().range(key, -properties.maxMessages.toLong(), -1) ?: return emptyList()
        return entries
            .mapNotNull { json ->
                runCatching { objectMapper.readValue(json, ConversationMessage::class.java) }
                    .onFailure { log.warn { "Skipping malformed conversation entry: ${it.message}" } }
                    .getOrNull()
            }
    }

    private fun keyFor(conversationId: String): String = "conversation:$conversationId"
}
