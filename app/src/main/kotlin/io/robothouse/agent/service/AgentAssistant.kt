package io.robothouse.agent.service

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.spring.AiService

@AiService
interface AgentAssistant {

    @SystemMessage("You are a helpful assistant. Answer questions concisely and accurately. Use available tools when appropriate.")
    fun chat(userMessage: String): String
}
