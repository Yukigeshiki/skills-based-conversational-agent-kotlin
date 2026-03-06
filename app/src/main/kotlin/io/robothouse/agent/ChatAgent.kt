package io.robothouse.agent

interface ChatAgent {
    fun chat(userMessage: String): String
}
