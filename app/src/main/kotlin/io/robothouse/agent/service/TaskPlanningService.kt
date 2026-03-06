package io.robothouse.agent.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.TaskPlan
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service

@Service
class TaskPlanningService(
    private val chatLanguageModel: ChatLanguageModel,
    private val agentProperties: AgentProperties
) {

    private val objectMapper = jacksonObjectMapper()

    fun createPlan(
        planningPrompt: String,
        userMessage: String,
        toolSpecifications: List<ToolSpecification>
    ): TaskPlan {
        val toolDescriptions = toolSpecifications.joinToString("\n") { spec ->
            "- ${spec.name()}: ${spec.description() ?: "No description"}"
        }

        val resolvedPrompt = planningPrompt.replace("{{tools}}", toolDescriptions)

        val request = ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from(resolvedPrompt),
                    UserMessage.from(userMessage)
                )
            )
            .build()

        val response = chatLanguageModel.chat(request)
        val responseText = response.aiMessage().text() ?: ""

        return parsePlan(responseText, userMessage)
    }

    internal fun parsePlan(responseText: String, userMessage: String): TaskPlan {
        val cleaned = stripCodeFences(responseText).trim()
        return try {
            val plan = objectMapper.readValue<TaskPlan>(cleaned)
            val cappedSteps = plan.steps.take(agentProperties.maxPlanSteps)
            plan.copy(steps = cappedSteps)
        } catch (e: Exception) {
            log.warn { "Failed to parse plan JSON, falling back to single-step plan: ${e.message}" }
            singleStepFallback(userMessage)
        }
    }

    private fun stripCodeFences(text: String): String {
        val fencePattern = Regex("```(?:json)?\\s*\\n?(.*?)\\n?\\s*```", RegexOption.DOT_MATCHES_ALL)
        return fencePattern.find(text)?.groupValues?.get(1) ?: text
    }

    private fun singleStepFallback(userMessage: String): TaskPlan {
        return TaskPlan(
            steps = listOf(
                PlanStep(
                    stepNumber = 1,
                    description = userMessage,
                    expectedTools = emptyList()
                )
            ),
            reasoning = "Fallback: could not parse plan"
        )
    }
}
