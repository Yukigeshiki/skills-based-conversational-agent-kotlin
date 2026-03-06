package io.robothouse.agent.service

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.service.AiServices
import io.robothouse.agent.ChatAgent
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.ToolRepository
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service

@Service
class DynamicAgentService(
    private val chatLanguageModel: ChatLanguageModel,
    private val chatMemory: ChatMemory,
    private val toolRepository: ToolRepository
) {

    fun chat(skill: Skill, userMessage: String): String {
        log.info { "Building agent with skill: ${skill.name}, tools: ${skill.toolNames}" }

        val tools = toolRepository.getToolsByNames(skill.toolNames)

        val builder = AiServices.builder(ChatAgent::class.java)
            .chatLanguageModel(chatLanguageModel)
            .chatMemory(chatMemory)
            .systemMessageProvider { skill.systemPrompt }

        tools.forEach { builder.tools(it) }

        val agent = builder.build()
        return agent.chat(userMessage)
    }
}
