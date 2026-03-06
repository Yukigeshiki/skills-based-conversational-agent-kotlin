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
import io.robothouse.agent.repository.ToolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeoutException

class DynamicAgentServiceTest {

    private val toolRepository: ToolRepository = mock()
    private val agentProperties = AgentProperties(maxToolExecutions = 10, toolExecutionTimeoutSeconds = 30)

    private val skill = Skill(
        name = "test-skill",
        description = "test",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool")
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
        whenever(toolRepository.getSpecificationsByNames(org.mockito.kotlin.any())).thenReturn(emptyList())
        whenever(toolRepository.getExecutorsByNames(org.mockito.kotlin.any())).thenReturn(emptyMap())

        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from("Hello!")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties)

        val result = service.chat(skill, "Hi")

        assertEquals("Hello!", result.response)
        assertEquals("test-skill", result.skill)
        assertEquals(0, result.toolExecutionCount)
        assertEquals(emptyList<Any>(), result.steps)
    }

    @Test
    fun `executes tool and returns response with steps`() {
        val toolSpec = ToolSpecification.builder().name("getCurrentDateTime").description("Gets time").build()
        whenever(toolRepository.getSpecificationsByNames(org.mockito.kotlin.any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(org.mockito.kotlin.any(), anyOrNull())).thenReturn("2026-03-06 18:00:00 JST")
        whenever(toolRepository.getExecutorsByNames(org.mockito.kotlin.any())).thenReturn(mapOf("getCurrentDateTime" to executor))

        val toolRequest = ToolExecutionRequest.builder()
            .name("getCurrentDateTime")
            .arguments("{\"timezone\": \"Asia/Tokyo\"}")
            .build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build(),
            ChatResponse.builder().aiMessage(AiMessage.from("The time in Tokyo is 18:00.")).build()
        )
        val service = DynamicAgentService(model, toolRepository, agentProperties)

        val result = service.chat(skill, "What time is it in Tokyo?")

        assertEquals("The time in Tokyo is 18:00.", result.response)
        assertEquals(1, result.toolExecutionCount)
        assertEquals("getCurrentDateTime", result.steps[0].toolName)
        assertEquals("2026-03-06 18:00:00 JST", result.steps[0].result)
    }

    @Test
    fun `stops at max tool executions`() {
        val toolSpec = ToolSpecification.builder().name("loopTool").description("Loops").build()
        whenever(toolRepository.getSpecificationsByNames(org.mockito.kotlin.any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(org.mockito.kotlin.any(), anyOrNull())).thenReturn("result")
        whenever(toolRepository.getExecutorsByNames(org.mockito.kotlin.any())).thenReturn(mapOf("loopTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("loopTool").arguments("{}").build()
        val alwaysToolCall = ChatResponse.builder()
            .aiMessage(AiMessage.from(listOf(toolRequest)))
            .build()

        val properties = AgentProperties(maxToolExecutions = 3, toolExecutionTimeoutSeconds = 30)
        val model = fakeChatModel(alwaysToolCall)
        val limitedService = DynamicAgentService(model, toolRepository, properties)

        val result = limitedService.chat(skill, "Loop forever")

        assertEquals(3, result.toolExecutionCount)
    }

    @Test
    fun `throws on timeout`() {
        val toolSpec = ToolSpecification.builder().name("slowTool").description("Slow").build()
        whenever(toolRepository.getSpecificationsByNames(org.mockito.kotlin.any())).thenReturn(listOf(toolSpec))

        val executor: ToolExecutor = mock()
        whenever(executor.execute(org.mockito.kotlin.any(), anyOrNull())).thenAnswer {
            Thread.sleep(10)
            "result"
        }
        whenever(toolRepository.getExecutorsByNames(org.mockito.kotlin.any())).thenReturn(mapOf("slowTool" to executor))

        val toolRequest = ToolExecutionRequest.builder().name("slowTool").arguments("{}").build()
        val model = fakeChatModel(
            ChatResponse.builder().aiMessage(AiMessage.from(listOf(toolRequest))).build()
        )

        val properties = AgentProperties(maxToolExecutions = 10, toolExecutionTimeoutSeconds = 0)
        val timeoutService = DynamicAgentService(model, toolRepository, properties)

        assertThrows<TimeoutException> {
            timeoutService.chat(skill, "Hi")
        }
    }
}
