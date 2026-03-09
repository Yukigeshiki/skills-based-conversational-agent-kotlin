package io.robothouse.agent.service

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.model.ConversationMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskPlanningServiceTest {

    private val agentProperties = AgentProperties(
        maxToolExecutions = 10,
        toolExecutionTimeoutSeconds = 30,
        maxPlanSteps = 5
    )

    private var capturedRequest: ChatRequest? = null

    private fun fakeChatModel(response: String): ChatModel {
        return object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                capturedRequest = request
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
        val plan = service.createPlan("Simple question", emptyList())

        assertEquals(1, plan.steps.size)
        assertEquals("Simple request", plan.reasoning)
    }

    @Test
    fun `falls back to single-step plan on malformed JSON`() {
        val service = TaskPlanningService(fakeChatModel("not valid json at all"), agentProperties)
        val plan = service.createPlan("Do something", emptyList())

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
        val plan = service.createPlan("Complex task", emptyList())

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
        val plan = service.createPlan("Question", emptyList())

        assertEquals(1, plan.steps.size)
        assertEquals("Wrapped in fences", plan.reasoning)
    }

    @Test
    fun `includes last assistant message as context when history is present`() {
        val json = """{"reasoning": "Follow-up", "steps": [{"stepNumber": 1, "description": "Answer", "expectedTools": []}]}"""
        val history = listOf(
            ConversationMessage(role = "user", content = "Tell me about tomatoes"),
            ConversationMessage(role = "assistant", content = "Tomatoes are wonderful fruits...")
        )

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        service.createPlan("Varieties.", emptyList(), history)

        val userMessageText = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertTrue(userMessageText.contains("Tomatoes are wonderful fruits..."))
        assertTrue(userMessageText.contains("Varieties."))
    }

    @Test
    fun `sends only user message when history is empty`() {
        val json = """{"reasoning": "Simple", "steps": [{"stepNumber": 1, "description": "Answer", "expectedTools": []}]}"""

        val service = TaskPlanningService(fakeChatModel(json), agentProperties)
        service.createPlan("Varieties.", emptyList(), emptyList())

        val userMessageText = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertEquals("Varieties.", userMessageText)
    }
}
