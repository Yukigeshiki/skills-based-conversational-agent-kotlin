package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.graph.agent.AgentGraphBuilder
import io.robothouse.agent.graph.agent.AgentGraphContext
import io.robothouse.agent.graph.agent.AgentGraphState
import io.robothouse.agent.graph.checkpoint.AgentGraphStateSerializer
import io.robothouse.agent.graph.checkpoint.PostgresCheckpointSaver
import io.robothouse.agent.graph.unwrapGraphException
import io.robothouse.agent.tool.DelegateToSkillExecutorFactory
import org.bsc.langgraph4j.RunnableConfig
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.PlanStepResult
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.RetrievedChunk
import io.robothouse.agent.model.TaskMemory
import io.robothouse.agent.model.ToolExecutionStep
import io.robothouse.agent.util.log
import org.springframework.beans.factory.annotation.Autowired
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
    private val toolService: ToolService,
    private val agentProperties: AgentProperties,
    private val taskPlanningService: TaskPlanningService,
    private val referenceRetrievalService: ReferenceRetrievalService,
    private val skillService: SkillService,
    private val delegateToSkillExecutorFactory: DelegateToSkillExecutorFactory,
    @param:Autowired(required = false) private val agentGraphStateSerializer: AgentGraphStateSerializer? = null,
    @param:Autowired(required = false) @param:Qualifier("agentCheckpointSaver") private val checkpointSaver: PostgresCheckpointSaver? = null
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
        conversationHistory: List<ConversationMessage> = emptyList(),
        conversationId: String? = null
    ): AgentResponse {
        log.debug { "Processing chat request for skill: name=${skill.name}, tools=${skill.toolNames}" }

        // 1. Decompose the request into a task plan (defaults to single step for simple requests)
        val plan = taskPlanningService.createPlan(userMessage, conversationHistory)
        log.debug { "Created plan with ${plan.steps.size} step(s): ${plan.reasoning}" }
        emitEvent(listener) { AgentEvent.PlanCreatedEvent(plan = plan) }

        // 2. Fast path: single-step plans skip the per-step loop and execute directly
        if (plan.steps.size <= 1) {
            val retrievedChunks = skill.id?.let { referenceRetrievalService.retrieveChunks(it, userMessage) } ?: emptyList()
            val effectiveSystemPrompt = buildSystemPrompt(skill, retrievedChunks)
            val specifications = toolService.getSpecificationsByNames(skill.toolNames)
            val executors = toolService.getExecutorsByNames(skill.toolNames)

            val response = executeStep(
                effectiveSystemPrompt,
                userMessage,
                specifications,
                executors,
                skill.name,
                listener,
                conversationHistory = conversationHistory,
                conversationId = conversationId
            )
            return response.copy(plan = plan)
        }

        // 3. Multi-step path: execute steps in dependency-based batches (independent steps run in parallel)
        val stepResults = executeStepsByBatch(plan.steps, skill, userMessage, listener, conversationId)
        val allToolSteps = stepResults.flatMap { it.toolSteps }

        // 4. Assemble step results into the final response
        val assembledResponse = assembleResults(stepResults)

        log.info { "Completed planned chat for skill: name=${skill.name}, steps=${plan.steps.size}, toolExecutions=${allToolSteps.size}" }
        val agentResponse = AgentResponse(
            response = assembledResponse,
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
     * Executes plan steps in dependency-based batches. Steps within a batch
     * whose dependencies are all satisfied run concurrently on virtual threads.
     * Batches execute sequentially — the next batch starts only when all steps
     * in the current batch have completed.
     */
    private fun executeStepsByBatch(
        steps: List<PlanStep>,
        skill: Skill,
        userMessage: String,
        listener: AgentEventListener,
        conversationId: String?
    ): List<PlanStepResult> {
        val batches = computeBatches(steps)
        log.debug { "Computed ${batches.size} batch(es): ${batches.map { batch -> batch.map { it.stepNumber } }}" }

        val stepResults = mutableListOf<PlanStepResult>()
        val completedResults = mutableMapOf<Int, PlanStepResult>()
        var failed = false

        for (batch in batches) {
            if (failed) {
                batch.forEach { planStep -> skipStep(planStep, stepResults, listener) }
                continue
            }

            if (batch.size == 1) {
                // Single step — execute inline without thread overhead
                val planStep = batch.first()
                val stepSkill = resolveStepSkill(planStep, skill)
                prepareAndEmitStepStart(planStep, stepSkill, listener)
                val context = buildDependencyContext(planStep, completedResults, userMessage)

                if (!executeAndRecordStep(planStep, stepSkill, context, listener, conversationId, stepResults)) {
                    failed = true
                }
                stepResults.last().let { completedResults[planStep.stepNumber] = it }
            } else {
                // Multiple steps — execute concurrently with virtual threads
                val batchResults = executeBatchConcurrently(batch, skill, userMessage, completedResults, listener, conversationId)
                for (result in batchResults) {
                    stepResults.add(result)
                    completedResults[result.step.stepNumber] = result
                    if (result.status == PlanStepStatus.FAILED) {
                        failed = true
                    }
                }
            }
        }

        return stepResults
    }

    /**
     * Executes a batch of independent steps concurrently using virtual threads.
     * Returns results sorted by step number for deterministic output.
     */
    private fun executeBatchConcurrently(
        batch: List<PlanStep>,
        skill: Skill,
        userMessage: String,
        completedResults: Map<Int, PlanStepResult>,
        listener: AgentEventListener,
        conversationId: String?
    ): List<PlanStepResult> {
        val executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

        val futures = batch.map { planStep ->
            val stepSkill = resolveStepSkill(planStep, skill)
            val stepChunks = stepSkill.id?.let {
                referenceRetrievalService.retrieveChunks(it, planStep.description)
            } ?: emptyList()
            val stepSystemPrompt = buildSystemPrompt(stepSkill, stepChunks)
            val stepSpecs = toolService.getSpecificationsByNames(stepSkill.toolNames)
            val stepExecutors = toolService.getExecutorsByNames(stepSkill.toolNames)
            val context = buildDependencyContext(planStep, completedResults, userMessage)

            planStep to executor.submit(java.util.concurrent.Callable {
                emitEvent(listener) {
                    AgentEvent.PlanStepStartedEvent(
                        stepNumber = planStep.stepNumber,
                        description = planStep.description,
                        skillName = stepSkill.name
                    )
                }
                executeStep(
                    stepSystemPrompt, context, stepSpecs, stepExecutors, stepSkill.name, listener,
                    emitFinalResponse = false, conversationId = conversationId
                )
            })
        }

        val results = futures.sortedBy { it.first.stepNumber }.map { (planStep, future) ->
            try {
                val response = future.get()
                emitEvent(listener) {
                    AgentEvent.PlanStepCompletedEvent(
                        stepNumber = planStep.stepNumber,
                        status = PlanStepStatus.COMPLETED,
                        response = response.response
                    )
                }
                PlanStepResult(
                    step = planStep,
                    status = PlanStepStatus.COMPLETED,
                    response = response.response,
                    toolSteps = response.steps,
                    iterations = response.iterations
                )
            } catch (e: Exception) {
                val message = e.cause?.message ?: e.message ?: "Step failed"
                log.warn { "Plan step ${planStep.stepNumber} failed: $message" }
                emitEvent(listener) {
                    AgentEvent.PlanStepCompletedEvent(
                        stepNumber = planStep.stepNumber,
                        status = PlanStepStatus.FAILED,
                        response = message
                    )
                }
                PlanStepResult(step = planStep, status = PlanStepStatus.FAILED, response = message)
            }
        }

        executor.shutdown()
        return results
    }

    /**
     * Groups plan steps into execution batches based on their declared
     * dependencies. Steps whose dependencies are all satisfied by prior
     * batches are grouped together. Falls back to sequential execution
     * if circular dependencies are detected.
     */
    private fun computeBatches(steps: List<PlanStep>): List<List<PlanStep>> {
        val batches = mutableListOf<List<PlanStep>>()
        val completed = mutableSetOf<Int>()
        val remaining = steps.toMutableList()

        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { step ->
                step.dependsOn.all { dep -> dep in completed }
            }
            if (ready.isEmpty()) {
                log.warn { "Circular or invalid dependencies detected — executing remaining steps sequentially" }
                batches.add(remaining.toList())
                break
            }
            batches.add(ready)
            completed.addAll(ready.map { it.stepNumber })
            remaining.removeAll(ready.toSet())
        }

        return batches
    }

    /**
     * Builds the user message context for a step using only its declared
     * dependencies' results, rather than all prior results. This allows
     * parallel steps to receive only the context they need.
     */
    private fun buildDependencyContext(
        planStep: PlanStep,
        completedResults: Map<Int, PlanStepResult>,
        userMessage: String
    ): String {
        val depResults = planStep.dependsOn.mapNotNull { completedResults[it] }
        return if (depResults.isNotEmpty()) {
            val depSummary = depResults.joinToString("\n") { result ->
                "Step ${result.step.stepNumber} (${result.status}): ${result.response}"
            }
            "Prior step results:\n$depSummary\n\nNow execute this step: ${planStep.description}"
        } else {
            "Original request: $userMessage\n\nExecute this step: ${planStep.description}"
        }
    }

    /**
     * Resolves the skill for a plan step, falling back to the routed skill
     * if the step's skill name is not found.
     */
    private fun resolveStepSkill(planStep: PlanStep, defaultSkill: Skill): Skill {
        return planStep.skillName?.let { name ->
            skillService.findByName(name) ?: run {
                log.warn { "Unknown skill '$name' in plan step ${planStep.stepNumber}, using routed skill" }
                defaultSkill
            }
        } ?: defaultSkill
    }

    /**
     * Prepares a step's context (RAG, system prompt, tools) and emits
     * the [AgentEvent.PlanStepStartedEvent].
     */
    private fun prepareAndEmitStepStart(
        planStep: PlanStep,
        stepSkill: Skill,
        listener: AgentEventListener
    ) {
        log.debug { "Executing plan step ${planStep.stepNumber}: ${planStep.description}" }
        emitEvent(listener) {
            AgentEvent.PlanStepStartedEvent(
                stepNumber = planStep.stepNumber,
                description = planStep.description,
                skillName = stepSkill.name
            )
        }
    }

    /**
     * Executes a single plan step and records the result. Returns true if
     * the step completed successfully, false if it failed.
     */
    private fun executeAndRecordStep(
        planStep: PlanStep,
        stepSkill: Skill,
        context: String,
        listener: AgentEventListener,
        conversationId: String?,
        stepResults: MutableList<PlanStepResult>
    ): Boolean {
        val stepChunks = stepSkill.id?.let {
            referenceRetrievalService.retrieveChunks(it, planStep.description)
        } ?: emptyList()
        val stepSystemPrompt = buildSystemPrompt(stepSkill, stepChunks)
        val stepSpecs = toolService.getSpecificationsByNames(stepSkill.toolNames)
        val stepExecutors = toolService.getExecutorsByNames(stepSkill.toolNames)

        return try {
            val stepResponse = executeStep(
                stepSystemPrompt, context, stepSpecs, stepExecutors, stepSkill.name, listener,
                emitFinalResponse = false, conversationId = conversationId
            )
            stepResults.add(
                PlanStepResult(
                    step = planStep, status = PlanStepStatus.COMPLETED, response = stepResponse.response,
                    toolSteps = stepResponse.steps, iterations = stepResponse.iterations
                )
            )
            emitEvent(listener) {
                AgentEvent.PlanStepCompletedEvent(stepNumber = planStep.stepNumber, status = PlanStepStatus.COMPLETED, response = stepResponse.response)
            }
            true
        } catch (e: Exception) {
            log.warn { "Plan step ${planStep.stepNumber} failed for skill: name=${stepSkill.name}, error: ${e.message}" }
            stepResults.add(
                PlanStepResult(step = planStep, status = PlanStepStatus.FAILED, response = e.message ?: "Step failed")
            )
            emitEvent(listener) {
                AgentEvent.PlanStepCompletedEvent(stepNumber = planStep.stepNumber, status = PlanStepStatus.FAILED, response = e.message ?: "Step failed")
            }
            false
        }
    }

    /**
     * Marks a plan step as skipped and records the result.
     */
    private fun skipStep(
        planStep: PlanStep,
        stepResults: MutableList<PlanStepResult>,
        listener: AgentEventListener
    ) {
        log.debug { "Skipping plan step ${planStep.stepNumber} due to earlier failure" }
        stepResults.add(PlanStepResult(step = planStep, status = PlanStepStatus.SKIPPED, response = "Skipped due to failure of a prior step"))
        emitEvent(listener) {
            AgentEvent.PlanStepCompletedEvent(stepNumber = planStep.stepNumber, status = PlanStepStatus.SKIPPED, response = "Skipped due to failure of a prior step")
        }
    }

    /**
     * Builds the effective system prompt for a skill by appending the response template
     * and any RAG-retrieved reference chunks.
     */
    private fun buildSystemPrompt(skill: Skill, retrievedChunks: List<RetrievedChunk>): String {
        val builder = StringBuilder(skill.systemPrompt)

        val template = skill.responseTemplate
        if (!template.isNullOrBlank()) {
            builder.append("\n\n## Response Template\nUse the following template to structure your response:\n")
            builder.append(template)
        }

        if (retrievedChunks.isNotEmpty()) {
            builder.append("\n\n## Reference Context\n")
            builder.append("The following excerpts were retrieved from reference materials and may be relevant.\n")
            builder.append("If the excerpts don't contain relevant information, rely on your general knowledge.\n")
            for (chunk in retrievedChunks) {
                builder.append("\n### ${chunk.referenceName} (section ${chunk.chunkIndex + 1})\n")
                builder.append(chunk.content)
            }
        }

        return builder.toString()
    }

    /**
     * Runs a single agent loop using a LangGraph4j StateGraph: sends messages
     * to the LLM and executes tool calls iteratively until the LLM responds
     * with text or the maximum number of iterations is reached.
     *
     * The graph has two nodes forming a cycle:
     * - call_llm: calls the LLM and decides whether to continue or finish
     * - execute_tools: executes requested tools and feeds results back
     */
    private fun executeStep(
        systemPrompt: String,
        userMessage: String,
        specifications: List<ToolSpecification>,
        executors: Map<String, ToolExecutor>,
        skillName: String,
        listener: AgentEventListener = AgentEventListener.NOOP,
        emitFinalResponse: Boolean = true,
        conversationHistory: List<ConversationMessage> = emptyList(),
        conversationId: String? = null,
        delegationDepth: Int = 0
    ): AgentResponse {
        val messages = mutableListOf<ChatMessage>(SystemMessage.from(systemPrompt))
        conversationHistory.forEach { msg ->
            when (msg.role) {
                "user" -> appendUserMessage(messages, msg.content)
                "assistant" -> appendAssistantMessage(messages, msg.content)
            }
        }
        appendUserMessage(messages, userMessage)

        // Inject the delegateToSkill meta-tool alongside the skill's own tools
        val delegateExecutor = delegateToSkillExecutorFactory.createExecutor(
            currentDepth = delegationDepth,
            maxDepth = agentProperties.maxDelegationDepth,
            currentSkillName = skillName,
            listener = listener,
            conversationId = conversationId,
            delegateFn = ::executeStepForDelegation
        )
        val allSpecifications = specifications + delegateToSkillExecutorFactory.specification()
        val allExecutors = executors + (DelegateToSkillExecutorFactory.TOOL_NAME to delegateExecutor)

        val taskMemory = TaskMemory()

        val ctx = AgentGraphContext(
            agentChatModel = agentChatModel,
            systemPrompt = systemPrompt,
            skillName = skillName,
            specifications = allSpecifications,
            executors = allExecutors,
            listener = listener,
            emitFinalResponse = emitFinalResponse,
            taskMemory = taskMemory,
            maxIterations = agentProperties.maxIterations,
            startTime = System.currentTimeMillis(),
            timeoutMillis = agentProperties.toolExecutionTimeoutSeconds * 1000,
            emitEvent = ::emitEvent,
            executeTool = ::executeTool,
            checkTimeout = ::checkTimeout,
            stateSerializer = agentGraphStateSerializer,
            checkpointSaver = checkpointSaver,
            conversationId = conversationId
        )

        val compiledGraph = AgentGraphBuilder.build(ctx)

        val initialState = mapOf(
            AgentGraphState.MESSAGES to messages.toList(),
            AgentGraphState.ITERATION to 1,
            AgentGraphState.DONE to false,
            AgentGraphState.RESPONSE to ""
        )

        val runnableConfig = checkpointSaver?.let {
            RunnableConfig.builder()
                .threadId("agent:${conversationId ?: "unknown"}:${System.nanoTime()}")
                .build()
        }

        val finalState = try {
            val result = if (runnableConfig != null) {
                compiledGraph.invoke(initialState, runnableConfig)
            } else {
                compiledGraph.invoke(initialState)
            }
            result.orElseThrow {
                log.warn { "Agent graph produced no final state for skill: name=$skillName" }
                IllegalStateException("Agent graph produced no final state")
            }
        } catch (e: Exception) {
            val unwrapped = unwrapGraphException(e)
            log.warn { "Agent graph execution failed for skill: name=$skillName, error=${unwrapped.message}" }
            throw unwrapGraphException(e)
        }

        val result = AgentGraphState(finalState.data())
        log.info { "Agent completed for skill: name=$skillName, toolExecutions=${result.steps.size}" }

        return AgentResponse(
            response = result.response,
            skill = skillName,
            steps = result.steps,
            toolExecutionCount = result.steps.size,
            iterations = taskMemory.iterations
        )
    }

    /**
     * Entry point for skill-to-skill delegation called by the
     * [DelegateToSkillExecutorFactory]'s executor. Resolves the target
     * skill's RAG context, system prompt, and tools, then executes a
     * nested agent loop at the given delegation depth.
     */
    internal fun executeStepForDelegation(
        skill: Skill,
        request: String,
        delegationDepth: Int,
        listener: AgentEventListener,
        conversationId: String?
    ): AgentResponse {
        val retrievedChunks = skill.id?.let {
            referenceRetrievalService.retrieveChunks(it, request)
        } ?: emptyList()
        val systemPrompt = buildSystemPrompt(skill, retrievedChunks)
        val specifications = toolService.getSpecificationsByNames(skill.toolNames)
        val executors = toolService.getExecutorsByNames(skill.toolNames)

        return executeStep(
            systemPrompt = systemPrompt,
            userMessage = request,
            specifications = specifications,
            executors = executors,
            skillName = skill.name,
            listener = listener,
            emitFinalResponse = false,
            conversationId = conversationId,
            delegationDepth = delegationDepth
        )
    }

    /**
     * Deterministically assembles step responses into a final response.
     * Completed results are joined with separators. Failed and skipped steps
     * are appended as a status summary, so no information is lost.
     */
    private fun assembleResults(stepResults: List<PlanStepResult>): String {
        val completedResults = stepResults.filter { it.status == PlanStepStatus.COMPLETED }
        if (completedResults.isEmpty()) {
            return "I was unable to complete any of the requested steps."
        }

        val assembled = if (completedResults.size == 1) {
            completedResults.first().response
        } else {
            completedResults.joinToString("\n\n---\n\n") { it.response }
        }

        val nonCompleted = stepResults.filter { it.status != PlanStepStatus.COMPLETED }
        if (nonCompleted.isEmpty()) {
            return assembled
        }

        val statusSummary = nonCompleted.joinToString("\n") { result ->
            "- Step ${result.step.stepNumber} (${result.step.description}): ${result.status} — ${result.response}"
        }
        return "$assembled\n\n---\n\n*Some steps could not be completed:*\n$statusSummary"
    }

    /**
     * Executes a single tool request, emitting started/completed events and
     * returning a [ToolExecutionStep] with the result. Falls back to an error
     * result if the executor is missing or throws.
     */
    private fun executeTool(
        toolRequest: ToolExecutionRequest,
        executors: Map<String, ToolExecutor>,
        iteration: Int,
        listener: AgentEventListener
    ): ToolExecutionStep {
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

        log.debug { "Tool completed: name=${toolRequest.name()}, error=$isError" }
        return ToolExecutionStep(
            toolName = toolRequest.name(),
            arguments = toolRequest.arguments(),
            result = result,
            error = isError
        )
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
     * Synchronized to prevent interleaved event delivery during parallel
     * step execution.
     */
    private fun emitEvent(listener: AgentEventListener, eventSupplier: () -> AgentEvent) {
        try {
            synchronized(listener) {
                listener.onEvent(eventSupplier())
            }
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
