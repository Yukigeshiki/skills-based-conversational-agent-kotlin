package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.config.AgentProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TaskPlanningServiceTest {

    private val agentProperties = AgentProperties(
        maxToolExecutions = 10,
        toolExecutionTimeoutSeconds = 30,
        maxPlanSteps = 5
    )

    private fun fakeChatModel(response: String): ChatLanguageModel {
        return object : ChatLanguageModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                return ChatResponse.builder().aiMessage(AiMessage.from(response)).build()
            }
        }
    }

    @Test
    fun `parses valid multi-step JSON plan`() {
        val json = """
            {
              "reasoning": "Need multiple steps",
              "steps": [
                {"stepNumber": 1, "description": "Step one", "expectedTools": ["ToolA"]},
                {"stepNumber": 2, "description": "Step two", "expectedTools": ["ToolB"]}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        val plan = service.createPlan(
            "Plan: {{tools}}",
            "Do something complex",
            listOf(ToolSpecification.builder().name("ToolA").description("A").build())
        )

        assertEquals(2, plan.steps.size)
        assertEquals("Need multiple steps", plan.reasoning)
        assertEquals("Step one", plan.steps[0].description)
        assertEquals(listOf("ToolA"), plan.steps[0].expectedTools)
    }

    @Test
    fun `parses single-step plan`() {
        val json = """
            {
              "reasoning": "Simple request",
              "steps": [
                {"stepNumber": 1, "description": "Just answer", "expectedTools": []}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        val plan = service.createPlan("Plan: {{tools}}", "Simple question", emptyList())

        assertEquals(1, plan.steps.size)
        assertEquals("Simple request", plan.reasoning)
    }

    @Test
    fun `falls back to single-step plan on malformed JSON`() {
        val service = TaskPlanningService(fakeChatModel("not valid json at all"), agentProperties)
        val plan = service.createPlan("Plan: {{tools}}", "Do something", emptyList())

        assertEquals(1, plan.steps.size)
        assertEquals("Do something", plan.steps[0].description)
        assertEquals("Fallback: could not parse plan", plan.reasoning)
    }

    @Test
    fun `caps steps at maxPlanSteps`() {
        val steps = (1..10).joinToString(",") {
            """{"stepNumber": $it, "description": "Step $it", "expectedTools": []}"""
        }
        val json = """{"reasoning": "Many steps", "steps": [$steps]}"""

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        val plan = service.createPlan("Plan: {{tools}}", "Complex task", emptyList())

        assertEquals(5, plan.steps.size)
    }

    @Test
    fun `strips markdown code fences from response`() {
        val json = """
            ```json
            {
              "reasoning": "Wrapped in fences",
              "steps": [{"stepNumber": 1, "description": "Do it", "expectedTools": []}]
            }
            ```
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        val plan = service.createPlan("Plan: {{tools}}", "Question", emptyList())

        assertEquals(1, plan.steps.size)
        assertEquals("Wrapped in fences", plan.reasoning)
    }
}
