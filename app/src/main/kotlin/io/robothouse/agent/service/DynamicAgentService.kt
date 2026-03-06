package io.robothouse.agent.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ToolExecutionStep
import io.robothouse.agent.repository.ToolRepository
import io.robothouse.agent.util.log
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.util.concurrent.TimeoutException

@Service
@EnableConfigurationProperties(AgentProperties::class)
class DynamicAgentService(
    private val chatLanguageModel: ChatLanguageModel,
    private val toolRepository: ToolRepository,
    private val agentProperties: AgentProperties
) {

    fun chat(skill: Skill, userMessage: String): AgentResponse {
        log.info { "Starting agent loop with skill: ${skill.name}, tools: ${skill.toolNames}" }

        val specifications = toolRepository.getSpecificationsByNames(skill.toolNames)
        val executors = toolRepository.getExecutorsByNames(skill.toolNames)

        val messages = mutableListOf<ChatMessage>(
            SystemMessage.from(skill.systemPrompt),
            UserMessage.from(userMessage)
        )

        val steps = mutableListOf<ToolExecutionStep>()
        val startTime = System.currentTimeMillis()
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000

        for (iteration in 1..agentProperties.maxToolExecutions) {
            checkTimeout(startTime, timeoutMillis)

            log.debug { "Agent loop iteration $iteration" }

            val request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(specifications)
                .build()

            val response = chatLanguageModel.chat(request)
            val aiMessage = response.aiMessage()
            messages.add(aiMessage)

            if (!aiMessage.hasToolExecutionRequests()) {
                log.info { "Agent completed after $iteration iteration(s), ${steps.size} tool execution(s)" }
                return AgentResponse(
                    response = aiMessage.text() ?: "",
                    skill = skill.name,
                    steps = steps,
                    toolExecutionCount = steps.size
                )
            }

            aiMessage.toolExecutionRequests().forEach { toolRequest ->
                log.info { "Executing tool: ${toolRequest.name()} with args: ${toolRequest.arguments()}" }

                val executor = executors[toolRequest.name()]
                    ?: throw IllegalStateException("No executor found for tool: ${toolRequest.name()}")

                val result = executor.execute(toolRequest, null)

                steps.add(ToolExecutionStep(
                    toolName = toolRequest.name(),
                    arguments = toolRequest.arguments(),
                    result = result
                ))

                messages.add(ToolExecutionResultMessage.from(toolRequest, result))
                log.info { "Tool ${toolRequest.name()} returned: $result" }
            }
        }

        log.warn { "Agent reached max tool executions (${agentProperties.maxToolExecutions})" }
        val lastAiText = messages.filterIsInstance<AiMessage>().lastOrNull()?.text() ?: ""
        return AgentResponse(
            response = lastAiText.ifBlank { "I reached the maximum number of tool executions. Here's what I found so far." },
            skill = skill.name,
            steps = steps,
            toolExecutionCount = steps.size
        )
    }

    private fun checkTimeout(startTime: Long, timeoutMillis: Long) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw TimeoutException("Agent loop exceeded timeout of ${agentProperties.toolExecutionTimeoutSeconds} seconds")
        }
    }
}
