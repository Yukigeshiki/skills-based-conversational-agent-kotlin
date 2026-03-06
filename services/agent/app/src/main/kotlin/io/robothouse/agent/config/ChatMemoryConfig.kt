package io.robothouse.agent.config

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures chat memory with a sliding window of recent messages.
 */
@Configuration
class ChatMemoryConfig {

    @Bean
    fun chatMemory(@Value("\${chat.memory.max-messages:20}") maxMessages: Int): ChatMemory =
        MessageWindowChatMemory.withMaxMessages(maxMessages)
}
