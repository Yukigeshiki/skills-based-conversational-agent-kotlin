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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Decomposes user requests into structured task plans via a single LLM call.
 *
 * Parses the LLM's JSON response into a [TaskPlan], stripping markdown code fences
 * and falling back to a single-step plan on parse failure.
 */
@Service
class TaskPlanningService(
    @param:Qualifier("lightChatModel") private val chatModel: ChatModel,
    private val agentProperties: AgentProperties
) {

    private val objectMapper = jacksonObjectMapper()

    companion object {
        val PLANNING_PROMPT = """
            |You are a task planner. Given a user request and a list of available tools, decide whether the request needs multiple execution steps.
            |
            |## When to use multiple steps
            |
            |Use multiple steps **only** when the request genuinely requires sequential reasoning — i.e. the output of one step informs the next, or multiple distinct tool calls must happen in sequence.
            |
            |Examples of multi-step requests:
            |- "What's the time difference between Tokyo and New York?" → step 1: get Tokyo time, step 2: get New York time (requires two separate tool calls whose results are combined)
            |- "Look up the weather then suggest an outfit" → step 1: fetch weather, step 2: reason about outfit based on the result
            |
            |## When to use a single step
            |
            |Use a single step for everything else, including:
            |- Questions that can be answered in one pass, even if the answer has multiple sections
            |- Requests that use a single tool call
            |- Conversational or knowledge-based queries with no tool use
            |
            |**Default to a single step.** When in doubt, use one step.
            |
            |## Available Tools
            |
            |{{tools}}
            |
            |## Response Format
            |
            |Respond with **only** a JSON object in this format:
            |
            |```json
            |{
            |  "reasoning": "Brief explanation of why this plan was chosen",
            |  "steps": [
            |    {
            |      "stepNumber": 1,
            |      "description": "What to do in this step",
            |      "expectedTools": ["toolName1"]
            |    }
            |  ]
            |}
            |```
        """.trimMargin()
    }

    /**
     * Creates a task plan by sending the planning prompt and user message to the LLM.
     *
     * Resolves the `{{tools}}` placeholder in the planning prompt with
     * available tool descriptions before making the LLM call.
     */
    fun createPlan(
        userMessage: String,
        toolSpecifications: List<ToolSpecification>
    ): TaskPlan {
        log.debug { "Creating plan with ${toolSpecifications.size} available tool(s)" }

        val toolDescriptions = toolSpecifications.joinToString("\n") { spec ->
            "- ${spec.name()}: ${spec.description() ?: "No description"}"
        }

        val resolvedPrompt = PLANNING_PROMPT.replace("{{tools}}", toolDescriptions)

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
