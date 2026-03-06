package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.tool.ToolExecutor
import dev.langchain4j.data.message.SystemMessage
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.TaskPlan
import io.robothouse.agent.repository.ToolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeoutException

class DynamicAgentServiceTest {

    private val toolRepository: ToolRepository = mock()
    private val taskPlanningService: TaskPlanningService = mock()
    private val agentProperties = AgentProperties(maxToolExecutions = 10, toolExecutionTimeoutSeconds = 30, maxPlanSteps = 10)

    private val skill = Skill(
        name = "test-skill",
        description = "test",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool")
    )

    private val skillWithPlanning = Skill(
        name = "planning-skill",
        description = "test",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool"),
        planningPrompt = "Plan the task"
    )

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
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skill, "Hi")

        assertEquals("Hello!", result.response)
        assertEquals("test-skill", result.skill)
        assertEquals(0, result.toolExecutionCount)
        assertEquals(emptyList<Any>(), result.steps)
    }

    @Test
    fun `executes tool and returns response with steps`() {
        val toolSpec = ToolSpecification.builder().name("getCurrentDateTime").description("Gets time").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("2026-03-06 18:00:00 JST")
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("getCurrentDateTime" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("getCurrentDateTime")
            .arguments("{\"timezone\": \"Asia/Tokyo\"}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("The time in Tokyo is 18:00.")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

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
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("result")
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("loopTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("loopTool").arguments("{}").build()
        val alwaysToolCall = ChatResponse.builder()
            .aiMessage(AiMessage.from(listOf(toolRequest)))
            .build()

        val properties = AgentProperties(maxToolExecutions = 3, toolExecutionTimeoutSeconds = 30, maxPlanSteps = 10)
        val model = fakeChatModel(alwaysToolCall)
        val limitedService = DynamicAgentService(model, toolRepository, properties, taskPlanningService)

        val result = limitedService.chat(skill, "Loop forever")

        assertEquals(3, result.toolExecutionCount)
    }

    @Test
    fun `throws on timeout`() {
        val toolSpec = ToolSpecification.builder().name("slowTool").description("Slow").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenAnswer {
            Thread.sleep(10)
            "result"
        }
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("slowTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("slowTool").arguments("{}").build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build()
        )

        val properties = AgentProperties(maxToolExecutions = 10, toolExecutionTimeoutSeconds = 0, maxPlanSteps = 10)
        val timeoutService = DynamicAgentService(model, toolRepository, properties, taskPlanningService)

        assertThrows<TimeoutException> {
            timeoutService.chat(skill, "Hi")
        }
    }

    @Test
    fun `skill without planningPrompt bypasses planning`() {
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Direct response")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skill, "Hi")

        assertEquals("Direct response", result.response)
        assertNull(result.plan)
        assertNull(result.planStepResults)
        verify(taskPlanningService, never()).createPlan(any(), any(), any())
    }

    @Test
    fun `single-step plan uses fast path`() {
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val singleStepPlan = TaskPlan(
            steps = listOf(PlanStep(stepNumber = 1, description = "Answer directly")),
            reasoning = "Simple request"
        )
        whenever(taskPlanningService.createPlan(any(), any(), any())).thenReturn(singleStepPlan)

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Simple answer")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skillWithPlanning, "What is 2+2?")

        assertEquals("Simple answer", result.response)
        assertNotNull(result.plan)
        assertEquals(1, result.plan!!.steps.size)
        assertNull(result.planStepResults)
    }

    @Test
    fun `multi-step plan executes each step and returns plan with step results`() {
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(emptyList())
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "Get time in NYC"),
                PlanStep(stepNumber = 2, description = "Get time in Tokyo")
            ),
            reasoning = "Need both times"
        )
        whenever(taskPlanningService.createPlan(any(), any(), any())).thenReturn(multiStepPlan)

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("NYC: 10am")).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Tokyo: 11pm")).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("NYC is 10am, Tokyo is 11pm.")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skillWithPlanning, "What time is it in NYC and Tokyo?")

        assertEquals("NYC is 10am, Tokyo is 11pm.", result.response)
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
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "This will have a missing tool", expectedTools = listOf("failTool")),
                PlanStep(stepNumber = 2, description = "This will succeed")
            ),
            reasoning = "Two steps"
        )
        whenever(taskPlanningService.createPlan(any(), any(), any())).thenReturn(multiStepPlan)

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
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skillWithPlanning, "Do two things")

        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
    }

    @Test
    fun `captures thought when AI returns text alongside tool calls`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("tool result")
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

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
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skill, "Help me")

        assertEquals(2, result.iterations.size)
        assertEquals("Let me look that up", result.iterations[0].thought)
        assertEquals(1, result.iterations[0].toolCalls.size)
        assertEquals("Here's the answer.", result.iterations[1].thought)
    }

    @Test
    fun `missing executor feeds error back to LLM instead of throwing`() {
        val toolSpec = ToolSpecification.builder().name("realTool").description("A tool").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val toolRequest = ToolExecutionRequest.builder()
            .name("fakeTool")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("Sorry, that tool doesn't exist.")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skill, "Use a tool")

        assertEquals("Sorry, that tool doesn't exist.", result.response)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps[0].error)
        assertTrue(result.steps[0].result.contains("No tool named 'fakeTool'"))
    }

    @Test
    fun `executor exception feeds error back to LLM instead of throwing`() {
        val toolSpec = ToolSpecification.builder().name("crashTool").description("Crashes").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenThrow(RuntimeException("Connection refused"))
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("crashTool" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("crashTool")
            .arguments("{}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("The tool crashed, sorry.")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skill, "Run the tool")

        assertEquals("The tool crashed, sorry.", result.response)
        assertEquals(1, result.steps.size)
        assertTrue(result.steps[0].error)
        assertTrue(result.steps[0].result.contains("Connection refused"))
    }

    @Test
    fun `error observations appear in scratchpad on subsequent iterations`() {
        val toolSpec = ToolSpecification.builder().name("errorTool").description("Errors").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenThrow(RuntimeException("Boom"))
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("errorTool" to executor))

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
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        service.chat(skill, "Try the tool")

        assertEquals(2, capturedSystemMessages.size)
        assertTrue(capturedSystemMessages[1].contains("ERROR [errorTool]"))
        assertTrue(capturedSystemMessages[1].contains("Decision guidance"))
    }

    @Test
    fun `injects scratchpad into system message on iteration 2+`() {
        val toolSpec = ToolSpecification.builder().name("myTool").description("A tool").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(any(), anyOrNull())).thenReturn("tool result")
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(mapOf("myTool" to executor))

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
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        service.chat(skill, "Do something")

        assertEquals(2, capturedSystemMessages.size)
        assertTrue(!capturedSystemMessages[0].contains("Your work so far"))
        assertTrue(capturedSystemMessages[1].contains("## Your work so far"))
        assertTrue(capturedSystemMessages[1].contains("--- Iteration 1 ---"))
    }
}
