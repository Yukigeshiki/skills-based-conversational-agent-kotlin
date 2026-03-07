package io.robothouse.agent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "conversation.memory")
data class ConversationMemoryProperties(
    val maxMessages: Int,
    val ttlHours: Long
)
