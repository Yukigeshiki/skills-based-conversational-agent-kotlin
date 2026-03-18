package io.robothouse.agent.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.model.ConversationMessage
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
    private val agentProperties: AgentProperties,
    private val skillCacheService: SkillCacheService
) {

    private val objectMapper = jacksonObjectMapper()

    companion object {
        val PLANNING_PROMPT = """
            |You are a task planner. Given a user request and a list of available skills, decide whether the request needs multiple execution steps.
            |
            |## When to use multiple steps
            |
            |Use multiple steps **only** when the request genuinely requires it — i.e. the output of one step informs the next, or multiple distinct tasks are needed.
            |
            |Examples of multi-step requests:
            |- "What's the time in Tokyo and New York?" → step 1: get Tokyo time (dependsOn: []), step 2: get New York time (dependsOn: []) — independent, can run in parallel
            |- "Look up the weather then suggest an outfit" → step 1: fetch weather (dependsOn: []), step 2: suggest outfit based on weather (dependsOn: [1]) — step 2 needs step 1's result
            |
            |## Step dependencies
            |
            |When creating multiple steps, declare dependencies using the `dependsOn` field:
            |- `"dependsOn": []` — step can run immediately, in parallel with other independent steps
            |- `"dependsOn": [1]` — step waits for step 1 to complete before starting
            |- Steps that need the output of earlier steps MUST list those steps in `dependsOn`
            |- Independent steps should have empty `dependsOn` so they can execute concurrently
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
            |## Available Skills
            |
            |Each skill has its own set of tools and expertise. Assign the most appropriate skill to each step.
            |
            |{{skills}}
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
            |      "expectedTools": ["toolName1"],
            |      "skillName": "skill-name",
            |      "dependsOn": []
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
        conversationHistory: List<ConversationMessage> = emptyList()
    ): TaskPlan {
        val skills = skillCacheService.findAll()
        log.debug { "Creating plan with ${skills.size} available skill(s)" }

        val skillDescriptions = skills.joinToString("\n") { "- ${it.name}: ${it.description}" }

        val resolvedPrompt = PLANNING_PROMPT.replace("{{skills}}", skillDescriptions)

        val contextualMessage = buildContextualMessage(userMessage, conversationHistory)

        val request = ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from(resolvedPrompt),
                    UserMessage.from(contextualMessage)
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

    private fun buildContextualMessage(userMessage: String, conversationHistory: List<ConversationMessage>): String {
        val lastAssistantMessage = conversationHistory.lastOrNull { it.role == "assistant" }
            ?: return userMessage

        return "Previous assistant response: ${lastAssistantMessage.content}\n\nUser follow-up: $userMessage"
    }
}
