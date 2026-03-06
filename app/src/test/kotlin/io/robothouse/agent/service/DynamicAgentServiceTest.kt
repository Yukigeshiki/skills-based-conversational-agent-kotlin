package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.tool.ToolExecutor
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.PlanStep
import io.robothouse.agent.model.PlanStepStatus
import io.robothouse.agent.model.TaskPlan
import io.robothouse.agent.repository.ToolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun fakeChatModel(vararg responses: ChatResponse): ChatLanguageModel {
        val queue = ArrayDeque(responses.toList())
        return object : ChatLanguageModel {
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
    }

    @Test
    fun `step failure captured as FAILED without aborting remaining steps`() {
        val toolSpec = ToolSpecification.builder().name("failTool").description("Fails").build()
        whenever(toolRepository.getSpecificationsByNames(any())).thenReturn(listOf(toolSpec))
        whenever(toolRepository.getExecutorsByNames(any())).thenReturn(emptyMap())

        val multiStepPlan = TaskPlan(
            steps = listOf(
                PlanStep(stepNumber = 1, description = "This will fail", expectedTools = listOf("failTool")),
                PlanStep(stepNumber = 2, description = "This will succeed")
            ),
            reasoning = "Two steps"
        )
        whenever(taskPlanningService.createPlan(any(), any(), any())).thenReturn(multiStepPlan)

        var callCount = 0
        val model = object : ChatLanguageModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                callCount++
                return when (callCount) {
                    1 -> {
                        // First step: return a tool call that will fail (no executor)
                        val toolRequest = ToolExecutionRequest.builder()
                            .name("failTool")
                            .arguments("{}")
                            .build()
                        ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build()
                    }
                    2 -> ChatResponse.builder().aiMessage(AiMessage.from("Step 2 done")).build()
                    else -> ChatResponse.builder().aiMessage(AiMessage.from("Synthesis")).build()
                }
            }
        }
        val service = DynamicAgentService(model, toolRepository, agentProperties, taskPlanningService)

        val result = service.chat(skillWithPlanning, "Do two things")

        assertNotNull(result.planStepResults)
        assertEquals(2, result.planStepResults!!.size)
        assertEquals(PlanStepStatus.FAILED, result.planStepResults!![0].status)
        assertEquals(PlanStepStatus.COMPLETED, result.planStepResults!![1].status)
    }
}
