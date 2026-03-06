package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
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

@Service
@EnableConfigurationProperties(AgentProperties::class)
class DynamicAgentService(
    private val chatLanguageModel: ChatLanguageModel,
    private val toolRepository: ToolRepository,
    private val agentProperties: AgentProperties,
    private val taskPlanningService: TaskPlanningService
) {

    fun chat(skill: Skill, userMessage: String): AgentResponse {
        log.info { "Starting agent loop with skill: ${skill.name}, tools: ${skill.toolNames}" }

        val specifications = toolRepository.getSpecificationsByNames(skill.toolNames)
        val executors = toolRepository.getExecutorsByNames(skill.toolNames)

        if (skill.planningPrompt == null) {
            return executeStep(skill.systemPrompt, userMessage, specifications, executors, skill.name)
        }

        val plan = taskPlanningService.createPlan(skill.planningPrompt!!, userMessage, specifications)
        log.info { "Created plan with ${plan.steps.size} step(s): ${plan.reasoning}" }

        if (plan.steps.size <= 1) {
            val response = executeStep(skill.systemPrompt, userMessage, specifications, executors, skill.name)
            return response.copy(plan = plan)
        }

        val stepResults = mutableListOf<PlanStepResult>()
        val allToolSteps = mutableListOf<ToolExecutionStep>()

        for (planStep in plan.steps) {
            log.info { "Executing plan step ${planStep.stepNumber}: ${planStep.description}" }

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
                log.warn { "Plan step ${planStep.stepNumber} failed: ${e.message}" }
                stepResults.add(
                    PlanStepResult(
                        step = planStep,
                        status = PlanStepStatus.FAILED,
                        response = e.message ?: "Step failed"
                    )
                )
            }
        }

        val synthesisResponse = synthesizeResults(skill.systemPrompt, userMessage, stepResults)

        return AgentResponse(
            response = synthesisResponse,
            skill = skill.name,
            steps = allToolSteps,
            toolExecutionCount = allToolSteps.size,
            plan = plan,
            planStepResults = stepResults
        )
    }

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
                    skill = skillName,
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
            skill = skillName,
            steps = steps,
            toolExecutionCount = steps.size
        )
    }

    private fun synthesizeResults(
        systemPrompt: String,
        originalMessage: String,
        stepResults: List<PlanStepResult>
    ): String {
        val resultsSummary = stepResults.joinToString("\n\n") { result ->
            "Step ${result.step.stepNumber} - ${result.step.description}\nStatus: ${result.status}\nResult: ${result.response}"
        }

        val synthesisPrompt = """$systemPrompt

You executed a multi-step plan. Synthesize the results into a coherent final response for the user.

Original request: $originalMessage

Step results:
$resultsSummary

Provide a clear, unified response based on all step results."""

        val request = ChatRequest.builder()
            .messages(
                listOf(
                    SystemMessage.from(synthesisPrompt),
                    UserMessage.from("Please synthesize the results above into a final response.")
                )
            )
            .build()

        val response = chatLanguageModel.chat(request)
        return response.aiMessage().text() ?: ""
    }

    private fun checkTimeout(startTime: Long, timeoutMillis: Long) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw TimeoutException("Agent loop exceeded timeout of ${agentProperties.toolExecutionTimeoutSeconds} seconds")
        }
    }
}
