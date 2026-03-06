package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.PlanStepResult
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.ToolExecutionStep
import io.robothouse.agent.repository.ToolRepository
import io.robothouse.agent.util.log
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.util.concurrent.TimeoutException

/**
 * Executes agent interactions for a given skill by running an LLM tool-execution loop.
 *
 * Supports optional task planning: skills with a planning prompt are decomposed
 * into multi-step plans before execution, with results synthesized into a final response.
 * Skills without a planning prompt execute directly in a single agent loop.
 */
@Service
@EnableConfigurationProperties(AgentProperties::class)
class DynamicAgentService(
    private val chatModel: ChatModel,
    private val toolRepository: ToolRepository,
    private val agentProperties: AgentProperties,
    private val taskPlanningService: TaskPlanningService
) {

    /**
     * Processes a chat request for the given skill.
     *
     * If the skill has a planning prompt, decomposes the request into a multi-step
     * plan and executes each step sequentially. Otherwise, executes directly
     * in a single agent loop.
     */
    fun chat(skill: Skill, userMessage: String): AgentResponse {
        log.debug { "Processing chat request for skill: name=${skill.name}, tools=${skill.toolNames}" }

        val specifications = toolRepository.getSpecificationsByNames(skill.toolNames)
        val executors = toolRepository.getExecutorsByNames(skill.toolNames)

        if (skill.planningPrompt == null) {
            return executeStep(skill.systemPrompt, userMessage, specifications, executors, skill.name)
        }

        val plan = taskPlanningService.createPlan(skill.planningPrompt!!, userMessage, specifications)
        log.debug { "Created plan with ${plan.steps.size} step(s): ${plan.reasoning}" }

        if (plan.steps.size <= 1) {
            val response = executeStep(skill.systemPrompt, userMessage, specifications, executors, skill.name)
            return response.copy(plan = plan)
        }

        val stepResults = mutableListOf<PlanStepResult>()
        val allToolSteps = mutableListOf<ToolExecutionStep>()

        for (planStep in plan.steps) {
            log.debug { "Executing plan step ${planStep.stepNumber}: ${planStep.description}" }

            val priorContext = if (stepResults.isNotEmpty()) {
                val priorSummary = stepResults.joinToString("\n") { result ->
                    "Step ${result.step.stepNumber} (${result.status}): ${result.response}"
                }
                "Prior step results:\n$priorSummary\n\nNow execute this step: ${planStep.description}"
            } else {
                "Original request: $userMessage\n\nExecute this step: ${planStep.description}"
            }

            try {
                val stepResponse = executeStep(
                    skill.systemPrompt, priorContext, specifications, executors, skill.name
                )
                allToolSteps.addAll(stepResponse.steps)
                stepResults.add(
                    PlanStepResult(
                        step = planStep,
                        status = PlanStepStatus.COMPLETED,
                        response = stepResponse.response,
                        toolSteps = stepResponse.steps
                    )
                )
            } catch (e: Exception) {
                log.warn { "Plan step ${planStep.stepNumber} failed for skill: name=${skill.name}, error: ${e.message}" }
                stepResults.add(
                    PlanStepResult(
                        step = planStep,
                        status = PlanStepStatus.FAILED,
                        response = e.message ?: "Step failed"
                    )
                )
            }
        }

        log.debug { "Synthesizing results for ${stepResults.size} plan steps" }
        val synthesisResponse = synthesizeResults(skill.systemPrompt, userMessage, stepResults)

        log.info { "Completed planned chat for skill: name=${skill.name}, steps=${plan.steps.size}, toolExecutions=${allToolSteps.size}" }
        return AgentResponse(
            response = synthesisResponse,
            skill = skill.name,
            steps = allToolSteps,
            toolExecutionCount = allToolSteps.size,
            plan = plan,
            planStepResults = stepResults
        )
    }

    /**
     * Runs a single agent loop: sends messages to the LLM and executes
     * tool calls iteratively until the LLM responds with text or
     * the maximum number of tool executions is reached.
     */
    private fun executeStep(
        systemPrompt: String,
        userMessage: String,
        specifications: List<ToolSpecification>,
        executors: Map<String, ToolExecutor>,
        skillName: String
    ): AgentResponse {
        val messages = mutableListOf<ChatMessage>(
            SystemMessage.from(systemPrompt),
            UserMessage.from(userMessage)
        )

        val steps = mutableListOf<ToolExecutionStep>()
        val startTime = System.currentTimeMillis()
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000

        for (iteration in 1..agentProperties.maxToolExecutions) {
            checkTimeout(startTime, timeoutMillis)

            log.debug { "Agent loop iteration $iteration for skill: name=$skillName" }

            val request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(specifications)
                .build()

            val response = chatModel.chat(request)
            val aiMessage = response.aiMessage()
            messages.add(aiMessage)

            if (!aiMessage.hasToolExecutionRequests()) {
                log.info { "Agent completed for skill: name=$skillName, iterations=$iteration, toolExecutions=${steps.size}" }
                return AgentResponse(
                    response = aiMessage.text() ?: "",
                    skill = skillName,
                    steps = steps,
                    toolExecutionCount = steps.size
                )
            }

            aiMessage.toolExecutionRequests().forEach { toolRequest ->
                log.debug { "Executing tool: name=${toolRequest.name()}, args=${toolRequest.arguments()}" }

                val executor = executors[toolRequest.name()]
                    ?: throw IllegalStateException("No executor found for tool: ${toolRequest.name()}")

                val result = executor.execute(toolRequest, null)

                steps.add(ToolExecutionStep(
                    toolName = toolRequest.name(),
                    arguments = toolRequest.arguments(),
                    result = result
                ))

                messages.add(ToolExecutionResultMessage.from(toolRequest, result))
                log.debug { "Tool completed: name=${toolRequest.name()}" }
            }
        }

        log.warn { "Agent reached max tool executions: skill=$skillName, maxExecutions=${agentProperties.maxToolExecutions}" }
        val lastAiText = messages.filterIsInstance<AiMessage>().lastOrNull()?.text() ?: ""
        return AgentResponse(
            response = lastAiText.ifBlank { "I reached the maximum number of tool executions. Here's what I found so far." },
            skill = skillName,
            steps = steps,
            toolExecutionCount = steps.size
        )
    }

    /**
     * Combines results from all plan steps into a coherent final response
     * via a dedicated LLM synthesis call.
     */
    private fun synthesizeResults(
        systemPrompt: String,
        originalMessage: String,
        stepResults: List<PlanStepResult>
    ): String {
        val resultsSummary = stepResults.joinToString("\n\n") { result ->
            "Step ${result.step.stepNumber} - ${result.step.description}\nStatus: ${result.status}\nResult: ${result.response}"
        }

        val synthesisPrompt = """
            |$systemPrompt
            |
            |You executed a multi-step plan. Synthesize the results into a coherent final response for the user.
            |
            |Original request: $originalMessage
            |
            |Step results:
            |$resultsSummary
            |
            |Provide a clear, unified response based on all step results.
        """.trimMargin()

        val request = ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from(synthesisPrompt),
                    UserMessage.from("Please synthesize the results above into a final response.")
                )
            )
            .build()

        val response = chatModel.chat(request)
        val text = response.aiMessage().text()
        if (text.isNullOrBlank()) {
            log.warn { "Synthesis returned empty response" }
            return "I was unable to synthesize a response from the plan step results."
        }
        return text
    }

    private fun checkTimeout(startTime: Long, timeoutMillis: Long) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw TimeoutException("Agent loop exceeded timeout of ${agentProperties.toolExecutionTimeoutSeconds} seconds")
        }
    }
}
