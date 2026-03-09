package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import io.robothouse.agent.model.ConversationMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentIteration
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.PlanStepResult
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.TaskMemory
import io.robothouse.agent.model.ToolCall
import io.robothouse.agent.model.ToolExecutionStep
import io.robothouse.agent.model.ToolObservation
import io.robothouse.agent.repository.ToolRepository
import io.robothouse.agent.util.log
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.TimeoutException

/**
 * Executes agent interactions for a given skill by running an LLM tool-execution loop.
 *
 * All skills use multistep planning: requests are decomposed into plans before execution,
 * with results synthesized into a final response. Simple requests produce single-step
 * plans and skip per-step overhead.
 */
@Service
class DynamicAgentService(
    @param:Qualifier("agentChatModel") private val agentChatModel: ChatModel,
    @param:Qualifier("lightChatModel") private val lightChatModel: ChatModel,
    private val toolRepository: ToolRepository,
    private val agentProperties: AgentProperties,
    private val taskPlanningService: TaskPlanningService
) {

    /**
     * Processes a chat request for the given skill.
     *
     * Decomposes the request into a multistep plan and executes each step sequentially.
     * Single-step plans skip per-step overhead and execute directly.
     */
    fun chat(
        skill: Skill,
        userMessage: String,
        listener: AgentEventListener = AgentEventListener.NOOP,
        conversationHistory: List<ConversationMessage> = emptyList()
    ): AgentResponse {
        log.debug { "Processing chat request for skill: name=${skill.name}, tools=${skill.toolNames}" }

        val specifications = toolRepository.getSpecificationsByNames(skill.toolNames)
        val executors = toolRepository.getExecutorsByNames(skill.toolNames)

        val plan = taskPlanningService.createPlan(userMessage, specifications, conversationHistory)
        log.debug { "Created plan with ${plan.steps.size} step(s): ${plan.reasoning}" }
        emitEvent(listener) { AgentEvent.PlanCreatedEvent(plan = plan) }

        if (plan.steps.size <= 1) {
            val response = executeStep(
                skill.systemPrompt,
                userMessage,
                specifications,
                executors,
                skill.name,
                listener,
                conversationHistory = conversationHistory
            )
            return response.copy(plan = plan)
        }

        val stepResults = mutableListOf<PlanStepResult>()
        val allToolSteps = mutableListOf<ToolExecutionStep>()

        for (planStep in plan.steps) {
            log.debug { "Executing plan step ${planStep.stepNumber}: ${planStep.description}" }
            emitEvent(listener) { AgentEvent.PlanStepStartedEvent(stepNumber = planStep.stepNumber, description = planStep.description) }

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
                    skill.systemPrompt, priorContext, specifications, executors, skill.name, listener, emitFinalResponse = false
                )
                allToolSteps.addAll(stepResponse.steps)
                val stepResult = PlanStepResult(
                    step = planStep,
                    status = PlanStepStatus.COMPLETED,
                    response = stepResponse.response,
                    toolSteps = stepResponse.steps,
                    iterations = stepResponse.iterations
                )
                stepResults.add(stepResult)
                emitEvent(listener) {
                    AgentEvent.PlanStepCompletedEvent(
                        stepNumber = planStep.stepNumber,
                        status = PlanStepStatus.COMPLETED,
                        response = stepResponse.response
                    )
                }
            } catch (e: Exception) {
                log.warn { "Plan step ${planStep.stepNumber} failed for skill: name=${skill.name}, error: ${e.message}" }
                stepResults.add(
                    PlanStepResult(
                        step = planStep,
                        status = PlanStepStatus.FAILED,
                        response = e.message ?: "Step failed"
                    )
                )
                emitEvent(listener) {
                    AgentEvent.PlanStepCompletedEvent(
                        stepNumber = planStep.stepNumber,
                        status = PlanStepStatus.FAILED,
                        response = e.message ?: "Step failed"
                    )
                }
            }
        }

        log.debug { "Synthesizing results for ${stepResults.size} plan steps" }
        val synthesisResponse = synthesizeResults(skill.systemPrompt, userMessage, stepResults)

        log.info { "Completed planned chat for skill: name=${skill.name}, steps=${plan.steps.size}, toolExecutions=${allToolSteps.size}" }
        val agentResponse = AgentResponse(
            response = synthesisResponse,
            skill = skill.name,
            steps = allToolSteps,
            toolExecutionCount = allToolSteps.size,
            plan = plan,
            planStepResults = stepResults,
            iterations = stepResults.flatMap { it.iterations }
        )
        emitEvent(listener) { AgentEvent.FinalResponseEvent(response = agentResponse.response, skill = agentResponse.skill) }
        return agentResponse
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
        skillName: String,
        listener: AgentEventListener = AgentEventListener.NOOP,
        emitFinalResponse: Boolean = true,
        conversationHistory: List<ConversationMessage> = emptyList()
    ): AgentResponse {
        val messages = mutableListOf<ChatMessage>(SystemMessage.from(systemPrompt))
        conversationHistory.forEach { msg ->
            when (msg.role) {
                "user" -> appendUserMessage(messages, msg.content)
                "assistant" -> appendAssistantMessage(messages, msg.content)
            }
        }
        appendUserMessage(messages, userMessage)

        val steps = mutableListOf<ToolExecutionStep>()
        val taskMemory = TaskMemory()
        val startTime = System.currentTimeMillis()
        val timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000

        for (iteration in 1..agentProperties.maxToolExecutions) {
            checkTimeout(startTime, timeoutMillis)

            if (iteration >= 2) {
                val scratchpad = taskMemory.toScratchpad()
                if (scratchpad != null) {
                    messages[0] = SystemMessage.from("$systemPrompt\n\n## Your work so far\n$scratchpad")
                }
            }

            log.debug { "Agent loop iteration $iteration for skill: name=$skillName" }
            emitEvent(listener) { AgentEvent.IterationStartedEvent(iterationNumber = iteration) }

            val requestBuilder = ChatRequest.builder()
                .messages(messages)
            if (specifications.isNotEmpty()) {
                requestBuilder.toolSpecifications(specifications)
            }
            val request = requestBuilder.build()

            val response = agentChatModel.chat(request)
            val aiMessage = response.aiMessage()
            messages.add(aiMessage)

            if (!aiMessage.hasToolExecutionRequests()) {
                taskMemory.addIteration(AgentIteration(
                    iterationNumber = iteration,
                    thought = aiMessage.text()
                ))
                log.info { "Agent completed for skill: name=$skillName, iterations=$iteration, toolExecutions=${steps.size}" }
                val agentResponse = AgentResponse(
                    response = aiMessage.text() ?: "",
                    skill = skillName,
                    steps = steps,
                    toolExecutionCount = steps.size,
                    iterations = taskMemory.iterations
                )
                if (emitFinalResponse) {
                    emitEvent(listener) { AgentEvent.FinalResponseEvent(response = agentResponse.response, skill = agentResponse.skill) }
                }
                return agentResponse
            }

            val thought = aiMessage.text()
            if (!thought.isNullOrBlank()) {
                emitEvent(listener) { AgentEvent.ThoughtEvent(iterationNumber = iteration, thought = thought) }
            }
            val iterationToolCalls = mutableListOf<ToolCall>()
            val iterationObservations = mutableListOf<ToolObservation>()

            aiMessage.toolExecutionRequests().forEach { toolRequest ->
                log.debug { "Executing tool: name=${toolRequest.name()}, args=${toolRequest.arguments()}" }
                emitEvent(listener) {
                    AgentEvent.ToolCallStartedEvent(
                        iterationNumber = iteration,
                        toolName = toolRequest.name(),
                        arguments = toolRequest.arguments()
                    )
                }

                val executor = executors[toolRequest.name()]

                val (result, isError) = if (executor == null) {
                    log.warn { "No executor found for tool: ${toolRequest.name()}" }
                    Pair(
                        "Error: No tool named '${toolRequest.name()}' is available. " +
                            "Available tools: ${executors.keys.joinToString(", ")}",
                        true
                    )
                } else {
                    try {
                        Pair(executor.execute(toolRequest, null), false)
                    } catch (e: Exception) {
                        log.warn { "Tool execution failed: name=${toolRequest.name()}, error=${e.message}" }
                        Pair("Error executing tool '${toolRequest.name()}': ${e.message}", true)
                    }
                }

                emitEvent(listener) {
                    AgentEvent.ToolCallCompletedEvent(
                        iterationNumber = iteration,
                        toolName = toolRequest.name(),
                        result = result,
                        error = isError
                    )
                }

                steps.add(ToolExecutionStep(
                    toolName = toolRequest.name(),
                    arguments = toolRequest.arguments(),
                    result = result,
                    error = isError
                ))

                iterationToolCalls.add(ToolCall(toolName = toolRequest.name(), arguments = toolRequest.arguments()))
                iterationObservations.add(ToolObservation(toolName = toolRequest.name(), result = result, error = isError))

                messages.add(ToolExecutionResultMessage.from(toolRequest, result))
                log.debug { "Tool completed: name=${toolRequest.name()}, error=$isError" }
            }

            taskMemory.addIteration(AgentIteration(
                iterationNumber = iteration,
                thought = thought,
                toolCalls = iterationToolCalls,
                observations = iterationObservations
            ))
        }

        log.warn { "Agent reached max tool executions: skill=$skillName, maxExecutions=${agentProperties.maxToolExecutions}" }
        val lastAiText = messages.filterIsInstance<AiMessage>().lastOrNull()?.text() ?: ""
        val agentResponse = AgentResponse(
            response = lastAiText.ifBlank { "I reached the maximum number of tool executions. Here's what I found so far." },
            skill = skillName,
            steps = steps,
            toolExecutionCount = steps.size,
            iterations = taskMemory.iterations
        )
        if (emitFinalResponse) {
            emitEvent(listener) { AgentEvent.FinalResponseEvent(response = agentResponse.response, skill = agentResponse.skill) }
        }
        return agentResponse
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

        val response = lightChatModel.chat(request)
        val text = response.aiMessage().text()
        if (text.isNullOrBlank()) {
            log.warn { "Synthesis returned empty response" }
            return "I was unable to synthesize a response from the plan step results."
        }
        return text
    }

    /**
     * Appends a user message to the message list, merging with the previous message
     * if it is also a UserMessage. This is a defensive measure to satisfy LLM APIs
     * that reject consecutive messages with the same role.
     */
    private fun appendUserMessage(messages: MutableList<ChatMessage>, text: String) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg is UserMessage) {
            log.warn { "Merging consecutive user messages — this may indicate a conversation history issue" }
            messages[messages.lastIndex] = UserMessage.from(lastMsg.singleText() + "\n" + text)
        } else {
            messages.add(UserMessage.from(text))
        }
    }

    /**
     * Appends an assistant message to the message list, merging with the previous message
     * if it is also an AiMessage. Same defensive measure as [appendUserMessage].
     */
    private fun appendAssistantMessage(messages: MutableList<ChatMessage>, text: String) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg is AiMessage) {
            log.warn { "Merging consecutive assistant messages — this may indicate a conversation history issue" }
            messages[messages.lastIndex] = AiMessage.from(lastMsg.text() + "\n" + text)
        } else {
            messages.add(AiMessage.from(text))
        }
    }

    /**
     * Safely delivers an event to the listener, catching and logging any
     * exception so that a faulty listener never interrupts the agent loop.
     */
    private fun emitEvent(listener: AgentEventListener, eventSupplier: () -> AgentEvent) {
        try {
            listener.onEvent(eventSupplier())
        } catch (e: Exception) {
            log.warn { "AgentEventListener threw: ${e.message}" }
        }
    }

    /**
     * Throws [TimeoutException] if the elapsed time since [startTime]
     * exceeds the configured [timeoutMillis] limit.
     */
    private fun checkTimeout(startTime: Long, timeoutMillis: Long) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw TimeoutException("Agent loop exceeded timeout of ${agentProperties.toolExecutionTimeoutSeconds} seconds")
        }
    }
}
