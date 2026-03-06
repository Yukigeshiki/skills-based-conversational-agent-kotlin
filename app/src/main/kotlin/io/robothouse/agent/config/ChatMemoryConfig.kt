package io.robothouse.agent.config

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatMemoryConfig {

    companion object {
        const val MAX_MESSAGES = 20
    }

    @Bean
    fun chatMemory(): ChatMemory =
        MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES)
}
