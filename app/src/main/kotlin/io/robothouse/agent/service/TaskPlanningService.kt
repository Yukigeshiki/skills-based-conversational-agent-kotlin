package io.robothouse.agent.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.TaskPlan
import io.robothouse.agent.util.log
import org.springframework.stereotype.Service

/**
 * Decomposes user requests into structured task plans via a single LLM call.
 *
 * Parses the LLM's JSON response into a [TaskPlan], stripping markdown code fences
 * and falling back to a single-step plan on parse failure.
 */
@Service
class TaskPlanningService(
    private val chatModel: ChatModel,
    private val agentProperties: AgentProperties
) {

    private val objectMapper = jacksonObjectMapper()

    /**
     * Creates a task plan by sending the planning prompt and user message to the LLM.
     *
     * Resolves the `{{tools}}` placeholder in the planning prompt with
     * available tool descriptions before making the LLM call.
     */
    fun createPlan(
        planningPrompt: String,
        userMessage: String,
        toolSpecifications: List<ToolSpecification>
    ): TaskPlan {
        log.debug { "Creating plan with ${toolSpecifications.size} available tool(s)" }

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

        val response = chatModel.chat(request)
        val responseText = response.aiMessage().text()

        if (responseText.isNullOrBlank()) {
            throw IllegalStateException("Task planning failed: LLM returned an empty response")
        }

        val plan = parsePlan(responseText, userMessage)
        log.info { "Created plan: steps=${plan.steps.size}, reasoning=${plan.reasoning}" }
        return plan
    }

    /**
     * Parses the LLM response text into a [TaskPlan].
     *
     * Strips markdown code fences, caps steps at [AgentProperties.maxPlanSteps],
     * and falls back to a single-step plan if JSON parsing fails.
     */
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
