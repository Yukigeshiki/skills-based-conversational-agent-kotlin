package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.tool.ToolExecutor
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.TaskPlan
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeoutException

class DynamicAgentServiceTest {

    private val toolService: ToolService = mock()
    private val taskPlanningService: TaskPlanningService = mock()
    private val referenceRetrievalService: ReferenceRetrievalService = mock()
    private val skillService: SkillService = mock()
    private val delegateToSkillExecutorFactory: io.robothouse.agent.tool.DelegateToSkillExecutorFactory = mock()
    private val pendingApprovalService: PendingApprovalService = mock()
    private val identityService: IdentityService = mock()
    private val agentProperties = AgentProperties(maxIterations = 10, toolExecutionTimeoutSeconds = 30, maxPlanSteps = 10, checkpointingEnabled = false, maxDelegationDepth = 2)

    private val skill = Skill(
        name = "test-skill",
        description = "test",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool")
    )

    private val singleStepPlan = TaskPlan(
        steps = listOf(PlanStep(stepNumber = 1, description = "Answer directly")),
        reasoning = "Simple request"
    )

    @BeforeEach
    fun setUp() {
        whenever(identityService.getSystemPrompt()).thenReturn("")
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(singleStepPlan)
        whenever(delegateToSkillExecutorFactory.specification(any(), any())).thenReturn(
            ToolSpecification.builder().name("delegateToSkill").description("Delegates to another skill").build()
        )
        whenever(delegateToSkillExecutorFactory.createExecutor(any(), any(), any(), any(), any(), anyOrNull(), any())).thenReturn(
            ToolExecutor { _, _ -> "delegation not expected in this test" }
        )
    }

    private fun fakeChatModel(vararg responses: ChatResponse): ChatModel {
        val queue = ArrayDeque(responses.toList())
        return object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                return queue.removeFirstOrNull() ?: responses.last()
            }
        }
    }

    @Test
    fun `returns response when no tools are called`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Hi")

        assertEquals("Hello!", result.response)
        assertEquals("test-skill", result.skill)
        assertEquals(0, result.toolExecutionCount)
        assertEquals(emptyList<Any>(), result.steps)
    }

    @Test
    fun `executes tool and returns response with steps`() {
        val toolSpec = ToolSpecification.builder().name("getCurrentDateTime").description("Gets time").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("2026-03-06 18:00:00 JST")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("getCurrentDateTime" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("getCurrentDateTime")
            .arguments("{\"timezone\": \"Asia/Tokyo\"}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("The time in Tokyo is 18:00.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "What time is it in Tokyo?")

        assertEquals("The time in Tokyo is 18:00.", result.response)
        assertEquals(1, result.toolExecutionCount)
        assertEquals("getCurrentDateTime", result.steps[0].toolName)
        assertEquals("2026-03-06 18:00:00 JST", result.steps[0].result)

        assertEquals(2, result.iterations.size)
        assertEquals(1, result.iterations[0].iterationNumber)
        assertEquals("getCurrentDateTime", result.iterations[0].toolCalls[0].toolName)
        assertEquals("2026-03-06 18:00:00 JST", result.iterations[0].observations[0].result)
        assertEquals(2, result.iterations[1].iterationNumber)
        assertEquals("The time in Tokyo is 18:00.", result.iterations[1].thought)
        assertTrue(result.iterations[1].toolCalls.isEmpty())
    }

    @Test
    fun `stops at max tool executions`() {
        val toolSpec = ToolSpecification.builder().name("loopTool").description("Loops").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("loopTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("loopTool").arguments("{}").build()
        val alwaysToolCall = ChatResponse.builder()
            .aiMessage(AiMessage.from(listOf(toolRequest)))
            .build()

        val properties = AgentProperties(maxIterations = 3, toolExecutionTimeoutSeconds = 30, maxPlanSteps = 10, checkpointingEnabled = false, maxDelegationDepth = 2)
        val model = fakeChatModel(alwaysToolCall)
        val limitedService = DynamicAgentService(model, toolService, properties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = limitedService.chat(skill, "Loop forever")

        assertEquals(3, result.toolExecutionCount)
    }

    @Test
    fun `throws on timeout`() {
        val toolSpec = ToolSpecification.builder().name("slowTool").description("Slow").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenAnswer {
            Thread.sleep(10)
            "result"
        }
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("slowTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("slowTool").arguments("{}").build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build()
        )

        val properties = AgentProperties(maxIterations = 10, toolExecutionTimeoutSeconds = 0, maxPlanSteps = 10, checkpointingEnabled = false, maxDelegationDepth = 2)
        val timeoutService = DynamicAgentService(model, toolService, properties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        assertThrows<TimeoutException> {
            timeoutService.chat(skill, "Hi")
        }
    }

    @Test
    fun `single-step plan uses fast path`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Simple answer")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "What is 2+2?")

        assertEquals("Simple answer", result.response)
        assertNotNull(result.plan)
        assertEquals(1, result.plan!!.steps.size)
        assertNull(result.planStepResults)
    }

    @Test
    fun `multi-step plan executes each step and returns plan with step results`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Get time in NYC"),
                PlanStep(stepNumber = 2, description = "Get time in Tokyo")
            ),
            reasoning = "Need both times"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val userMsg = request.messages().filterIsInstance<UserMessage>().last().singleText()
                val response = if (userMsg.contains("Get time in NYC")) "NYC: 10am" else "Tokyo: 11pm"
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "What time is it in NYC and Tokyo?")

        assertEquals("NYC: 10am\n\n---\n\nTokyo: 11pm", result.response)
        assertNotNull(result.plan)
        assertEquals(2, result.plan!!.steps.size)
        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
        assertEquals("NYC: 10am", result.planStepResults!![0].response)
        assertEquals("Tokyo: 11pm", result.planStepResults!![1].response)

        assertEquals(1, result.planStepResults!![0].iterations.size)
        assertEquals(1, result.planStepResults!![1].iterations.size)
        assertEquals(2, result.iterations.size)
    }

    @Test
    fun `step with missing tool recovers and completes`() {
        val toolSpec = ToolSpecification.builder().name("failTool").description("Fails").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "This will have a missing tool", expectedTools = listOf("failTool")),
                PlanStep(stepNumber = 2, description = "This will succeed")
            ),
            reasoning = "Two steps"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        var callCount = 0
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                callCount++
                return when (callCount) {
                    1 -> {
                        val toolRequest = ToolExecutionRequest.builder()
                            .name("failTool")
                            .arguments("{}")
                            .build()
                        ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build()
                    }
                    2 -> ChatResponse.builder().aiMessage(AiMessage.from("I couldn't find that tool")).build()
                    3 -> ChatResponse.builder().aiMessage(AiMessage.from("Step 2 done")).build()
                    else -> ChatResponse.builder().aiMessage(AiMessage.from("Synthesis")).build()
                }
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Do two things")

        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
    }

    @Test
    fun `skips remaining steps when a step throws an exception`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "This will throw", dependsOn = emptyList()),
                PlanStep(stepNumber = 2, description = "This should be skipped", dependsOn = listOf(1)),
                PlanStep(stepNumber = 3, description = "This should also be skipped", dependsOn = listOf(1))
            ),
            reasoning = "Three steps"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                throw RuntimeException("LLM service unavailable")
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Do three things")

        assertNotNull(result.planStepResults)
        assertEquals(3, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.FAILED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.SKIPPED, result.planStepResults!![1].status)
        assertEquals(PlanStepStatus.SKIPPED, result.planStepResults!![2].status)
        assertEquals("Skipped due to failure of a prior step", result.planStepResults!![1].response)
    }

    @Test
    fun `captures thought when AI returns text alongside tool calls`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("tool result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("myTool")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder()
                .aiMessage(AiMessage("Let me look that up", listOf(toolRequest)))
                .build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Here's the answer.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Help me")

        assertEquals(2, result.iterations.size)
        assertEquals("Let me look that up", result.iterations[0].thought)
        assertEquals(1, result.iterations[0].toolCalls.size)
        assertEquals("Here's the answer.", result.iterations[1].thought)
    }

    @Test
    fun `missing executor feeds error back to LLM instead of throwing`() {
        val toolSpec = ToolSpecification.builder().name("realTool").description("A tool").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val toolRequest = ToolExecutionRequest.builder()
            .name("fakeTool")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Sorry, that tool doesn't exist.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Use a tool")

        assertEquals("Sorry, that tool doesn't exist.", result.response)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps[0].error)
        assertTrue(result.steps[0].result.contains("No tool named 'fakeTool'"))
    }

    @Test
    fun `executor exception feeds error back to LLM instead of throwing`() {
        val toolSpec = ToolSpecification.builder().name("crashTool").description("Crashes").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenThrow(RuntimeException("Connection refused"))
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("crashTool" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("crashTool")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("The tool crashed, sorry.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Run the tool")

        assertEquals("The tool crashed, sorry.", result.response)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps[0].error)
        assertTrue(result.steps[0].result.contains("Connection refused"))
    }

    @Test
    fun `error observations appear in scratchpad on subsequent iterations`() {
        val toolSpec = ToolSpecification.builder().name("errorTool").description("Errors").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("errorTool" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("errorTool")
            .arguments("{}")
            .build()

        val capturedSystemMessages = mutableListOf<String>()
        val responseList = listOf(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("I see the error.")).build()
        )
        var responseIndex = 0
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return responseList[responseIndex++]
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skill, "Try the tool")

        assertEquals(2, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages[1].contains("ERROR [errorTool]"))
        assertTrue(capturedSystemMessages[1].contains("Decision guidance"))
    }

    @Test
    fun `listener receives events for simple chat`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val events = mutableListOf<AgentEvent>()
        service.chat(skill, "Hi", AgentEventListener { events.add(it) })

        assertTrue(events.any { it is AgentEvent.IterationStartedEvent })
        assertTrue(events.any { it is AgentEvent.FinalResponseEvent })
        val iterationEvent = events.filterIsInstance<AgentEvent.IterationStartedEvent>().first()
        assertEquals(1, iterationEvent.iterationNumber)
    }

    @Test
    fun `listener receives tool call events`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("myTool").arguments("{}").build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Done.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val events = mutableListOf<AgentEvent>()
        service.chat(skill, "Do it", AgentEventListener { events.add(it) })

        assertTrue(events.any { it is AgentEvent.ToolCallStartedEvent })
        assertTrue(events.any { it is AgentEvent.ToolCallCompletedEvent })
        val started = events.filterIsInstance<AgentEvent.ToolCallStartedEvent>().first()
        assertEquals("myTool", started.toolName)
        val completed = events.filterIsInstance<AgentEvent.ToolCallCompletedEvent>().first()
        assertEquals("myTool", completed.toolName)
        assertEquals("result", completed.result)
    }

    @Test
    fun `listener receives plan events with single FinalResponseEvent`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Step one"),
                PlanStep(stepNumber = 2, description = "Step two")
            ),
            reasoning = "Two steps"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val userMsg = request.messages().filterIsInstance<UserMessage>().last().singleText()
                val response = if (userMsg.contains("Step one")) "One" else "Two"
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val events = java.util.Collections.synchronizedList(mutableListOf<AgentEvent>())
        service.chat(skill, "Do two things", AgentEventListener { events.add(it) })

        assertTrue(events.any { it is AgentEvent.PlanCreatedEvent })
        assertEquals(2, events.filterIsInstance<AgentEvent.PlanStepStartedEvent>().size)
        assertEquals(2, events.filterIsInstance<AgentEvent.PlanStepCompletedEvent>().size)
        assertEquals(1, events.filterIsInstance<AgentEvent.FinalResponseEvent>().size)
        assertEquals("One\n\n---\n\nTwo", events.filterIsInstance<AgentEvent.FinalResponseEvent>().first().response)
    }

    @Test
    fun `listener exception does not break agent loop`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Hi", AgentEventListener { throw RuntimeException("Listener broke") })

        assertEquals("Hello!", result.response)
    }

    @Test
    fun `listener events are emitted in correct order`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("myTool").arguments("{}").build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage("Let me check", listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Done.")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val events = mutableListOf<AgentEvent>()
        service.chat(skill, "Do it", AgentEventListener { events.add(it) })

        val eventTypes = events.map { it.type }
        assertEquals(
            listOf(
                "plan_created",
                "iteration_started",
                "thought",
                "tool_call_started",
                "tool_call_completed",
                "iteration_started",
                "final_response"
            ),
            eventTypes
        )
    }

    @Test
    fun `injects conversation history into message list`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val capturedMessages = mutableListOf<List<ChatMessage>>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                capturedMessages.add(request.messages().toList())
                return ChatResponse.builder().aiMessage(AiMessage.from("I remember you!")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val history = listOf(
            ConversationMessage(role = "user", content = "My name is Alice"),
            ConversationMessage(role = "assistant", content = "Nice to meet you, Alice!")
        )

        val result = service.chat(skill, "What's my name?", conversationHistory = history)

        assertEquals("I remember you!", result.response)
        val messages = capturedMessages[0]
        // SystemMessage, UserMessage(history), AiMessage(history), UserMessage(current)
        assertEquals(4, messages.size)
        assertTrue(messages[0] is SystemMessage)
        assertTrue(messages[1] is UserMessage)
        assertEquals("My name is Alice", (messages[1] as UserMessage).singleText())
        assertTrue(messages[2] is AiMessage)
        assertEquals("Nice to meet you, Alice!", (messages[2] as AiMessage).text())
        assertTrue(messages[3] is UserMessage)
        assertEquals("What's my name?", (messages[3] as UserMessage).singleText())
    }

    @Test
    fun `includes response template in system prompt when present`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val skillWithTemplate = Skill(
            name = "template-skill",
            description = "test",
            systemPrompt = "You are a test assistant.",
            responseTemplate = "Subject: ...\nBody: ...",
            toolNames = emptyList()
        )

        val capturedSystemMessages = mutableListOf<String>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Done")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skillWithTemplate, "Write an email")

        assertEquals(1, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages[0].contains("## Response Template"))
        assertTrue(capturedSystemMessages[0].contains("Subject: ...\nBody: ..."))
    }

    @Test
    fun `does not include response template section when template is null`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val capturedSystemMessages = mutableListOf<String>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Done")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skill, "Hello")

        assertEquals(1, capturedSystemMessages.size)
        assertTrue(!capturedSystemMessages[0].contains("## Response Template"))
    }

    @Test
    fun `prepends identity system prompt before skill system prompt`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())
        whenever(identityService.getSystemPrompt()).thenReturn("You are a friendly pirate.")

        val capturedSystemMessages = mutableListOf<String>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Ahoy!")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skill, "Hello")

        assertEquals(1, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages[0].startsWith("You are a friendly pirate."))
        assertTrue(capturedSystemMessages[0].contains("You are a test assistant."))
    }

    @Test
    fun `does not prepend identity prompt when it is blank`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())
        whenever(identityService.getSystemPrompt()).thenReturn("")

        val capturedSystemMessages = mutableListOf<String>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skill, "Hello")

        assertEquals(1, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages[0].startsWith("You are a test assistant."))
    }

    @Test
    fun `injects scratchpad into system message on iteration 2+`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("tool result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("myTool")
            .arguments("{}")
            .build()

        val capturedSystemMessages = mutableListOf<String>()
        val responseList = listOf(
            ChatResponse.builder()
                .aiMessage(AiMessage(null, listOf(toolRequest)))
                .build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Done.")).build()
        )
        var responseIndex = 0
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return responseList[responseIndex++]
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        service.chat(skill, "Do something")

        assertEquals(2, capturedSystemMessages.size)
        assertTrue(!capturedSystemMessages[0].contains("Your work so far"))
        assertTrue(capturedSystemMessages[1].contains("## Your work so far"))
        assertTrue(capturedSystemMessages[1].contains("--- Iteration 1 ---"))
    }

    @Test
    fun `multi-step plan with per-step skill names resolves correct skill per step`() {
        val gardenSkill = Skill(
            id = UUID.randomUUID(),
            name = "garden",
            description = "Gardening advice",
            systemPrompt = "You are a gardening expert.",
            toolNames = emptyList()
        )
        val timeSkill = Skill(
            id = UUID.randomUUID(),
            name = "time",
            description = "Time queries",
            systemPrompt = "You are a time expert.",
            toolNames = emptyList()
        )

        whenever(skillService.findByName("garden")).thenReturn(gardenSkill)
        whenever(skillService.findByName("time")).thenReturn(timeSkill)
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Tell about tomatoes", skillName = "garden"),
                PlanStep(stepNumber = 2, description = "Get time in Tokyo", skillName = "time")
            ),
            reasoning = "Two different skills"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val capturedSystemMessages = java.util.Collections.synchronizedList(mutableListOf<String>())
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                val response = if (sysMsg.contains("gardening expert")) "Garden response" else "Time response"
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Tell me about tomatoes, then the time in Tokyo")

        assertEquals(2, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages.any { it.contains("gardening expert") })
        assertTrue(capturedSystemMessages.any { it.contains("time expert") })
        assertEquals("Garden response\n\n---\n\nTime response", result.response)
    }

    @Test
    fun `unknown skillName in plan step falls back to routed skill`() {
        whenever(skillService.findByName("nonexistent")).thenReturn(null)
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Step one", skillName = "nonexistent"),
                PlanStep(stepNumber = 2, description = "Step two")
            ),
            reasoning = "Fallback test"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val capturedSystemMessages = mutableListOf<String>()
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val sysMsg = request.messages().filterIsInstance<SystemMessage>().first().text()
                capturedSystemMessages.add(sysMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Done")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Do things")

        assertEquals(2, capturedSystemMessages.size)
        // Both steps should use the routed skill's system prompt
        assertTrue(capturedSystemMessages[0].contains("test assistant"))
        assertTrue(capturedSystemMessages[1].contains("test assistant"))
    }

    // --- Parallel execution tests ---

    @Test
    fun `parallel steps with dependsOn execute and return results in step order`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val parallelPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Get Tokyo time", dependsOn = emptyList()),
                PlanStep(stepNumber = 2, description = "Get NYC time", dependsOn = emptyList())
            ),
            reasoning = "Two independent lookups"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(parallelPlan)

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Tokyo: 3pm")).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("NYC: 1am")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "What time is it in Tokyo and NYC?")

        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
        assertEquals(1, result.planStepResults!![0].step.stepNumber)
        assertEquals(2, result.planStepResults!![1].step.stepNumber)
    }

    @Test
    fun `parallel batch followed by dependent step executes in order`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val plan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Get Tokyo time", dependsOn = emptyList()),
                PlanStep(stepNumber = 2, description = "Get NYC time", dependsOn = emptyList()),
                PlanStep(stepNumber = 3, description = "Compare times", dependsOn = listOf(1, 2))
            ),
            reasoning = "Two parallel, one dependent"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(plan)

        val capturedUserMessages = mutableListOf<String>()
        var callCount = 0
        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                callCount++
                val userMsg = request.messages().filterIsInstance<dev.langchain4j.data.message.UserMessage>().last().singleText()
                capturedUserMessages.add(userMsg)
                return ChatResponse.builder().aiMessage(AiMessage.from("Response $callCount")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Compare times in Tokyo and NYC")

        assertEquals(3, result.planStepResults!!.size)
        assertTrue(result.planStepResults!!.all { it.status == PlanStepStatus.COMPLETED })

        // Step 3 should receive prior results from steps 1 and 2
        val step3Context = capturedUserMessages.last()
        assertTrue(step3Context.contains("Prior step results"))
        assertTrue(step3Context.contains("Compare times"))
    }

    @Test
    fun `failure in parallel batch skips subsequent batches`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val plan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "This will fail", dependsOn = emptyList()),
                PlanStep(stepNumber = 2, description = "This runs in parallel", dependsOn = emptyList()),
                PlanStep(stepNumber = 3, description = "This depends on both", dependsOn = listOf(1, 2))
            ),
            reasoning = "Failure test"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(plan)

        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val userMsg = request.messages().filterIsInstance<dev.langchain4j.data.message.UserMessage>().last().singleText()
                // Fail deterministically based on step description, not call order
                if (userMsg.contains("This will fail")) throw RuntimeException("LLM unavailable")
                return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build()
            }
        }
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Do things")

        assertNotNull(result.planStepResults)
        assertEquals(3, result.planStepResults!!.size)

        // Step 1 failed deterministically, step 2 completed (ran in parallel), step 3 skipped
        assertEquals(PlanStepStatus.FAILED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
        assertEquals(PlanStepStatus.SKIPPED, result.planStepResults!![2].status)
    }

    @Test
    fun `circular dependencies fall back to sequential execution`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val plan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Depends on 2", dependsOn = listOf(2)),
                PlanStep(stepNumber = 2, description = "Depends on 1", dependsOn = listOf(1))
            ),
            reasoning = "Circular"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(plan)

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("One")).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Two")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Circular deps")

        // Should not deadlock — falls back to sequential
        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
    }

    @Test
    fun `steps without dependsOn run in parallel as independent`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val plan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Step one"),
                PlanStep(stepNumber = 2, description = "Step two")
            ),
            reasoning = "No deps — both independent"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(plan)

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("One")).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Two")).build()
        )
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(skill, "Do two things")

        assertEquals(2, result.planStepResults!!.size)
        assertTrue(result.planStepResults!!.all { it.status == PlanStepStatus.COMPLETED })
        assertEquals(1, result.planStepResults!![0].step.stepNumber)
        assertEquals(2, result.planStepResults!![1].step.stepNumber)
    }

    // --- Approval (human-in-the-loop) tests ---

    @Test
    fun `requiresApproval without checkpointing does not force single-step`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Step one"),
                PlanStep(stepNumber = 2, description = "Step two")
            ),
            reasoning = "Two steps"
        )
        whenever(taskPlanningService.createPlan(any(), any())).thenReturn(multiStepPlan)

        val approvalSkill = Skill(
            name = "approval-skill",
            description = "test",
            systemPrompt = "You need approval.",
            toolNames = emptyList(),
            requiresApproval = true
        )

        val model = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                val userMsg = request.messages().filterIsInstance<UserMessage>().last().singleText()
                val response = if (userMsg.contains("Step one")) "One" else "Two"
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
        // No checkpoint saver — requiresApproval cannot force single-step
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(approvalSkill, "Do two things")

        // Multi-step should still execute normally since checkpointing is disabled
        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertFalse(result.awaitingApproval)
    }

    @Test
    fun `requiresApproval is ignored when checkpointing is disabled`() {
        whenever(toolService.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolService.getExecutorsByNames(any())).thenReturn(emptyMap())

        val approvalSkill = Skill(
            name = "approval-skill",
            description = "test",
            systemPrompt = "You need approval.",
            toolNames = listOf("TestTool"),
            requiresApproval = true
        )

        val toolRequest = ToolExecutionRequest.builder()
            .name("getCurrentDateTime")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Done.")).build()
        )

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("result")
        whenever(toolService.getExecutorsByNames(any())).thenReturn(mapOf("getCurrentDateTime" to executor))

        // No checkpoint saver — requiresApproval should be silently ignored
        val service = DynamicAgentService(model, toolService, agentProperties, taskPlanningService, referenceRetrievalService, skillService, delegateToSkillExecutorFactory, pendingApprovalService, identityService)

        val result = service.chat(approvalSkill, "What time?")

        // Tools should execute normally without approval
        assertEquals("Done.", result.response)
        assertFalse(result.awaitingApproval)
    }
}
