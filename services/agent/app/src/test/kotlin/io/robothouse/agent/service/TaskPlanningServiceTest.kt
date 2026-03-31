package io.robothouse.agent.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.config.AgentProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.service.SkillCacheService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TaskPlanningServiceTest {

    private val agentProperties = AgentProperties(
        maxIterations = 10,
        toolExecutionTimeoutSeconds = 30,
        maxPlanSteps = 5,
        checkpointingEnabled = false,
        maxDelegationDepth = 2,
    )

    private val skillCacheService: SkillCacheService = mock()
    private var capturedRequest: ChatRequest? = null

    private val testSkills = listOf(
        Skill(name = "general", description = "General knowledge and conversation", systemPrompt = "You are helpful.", toolNames = emptyList()),
        Skill(name = "time", description = "Time and timezone queries", systemPrompt = "You handle time.", toolNames = listOf("getCurrentDateTime"))
    )

    @BeforeEach
    fun setUp() {
        whenever(skillCacheService.findAll()).thenReturn(testSkills)
    }

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

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Do something complex")

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

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Simple question")

        assertEquals(1, plan.steps.size)
        assertEquals("Simple request", plan.reasoning)
    }

    @Test
    fun `falls back to single-step plan on malformed JSON`() {
        val service = TaskPlanningService(fakeChatModel("not valid json at all"), agentProperties, skillCacheService)
        val plan = service.createPlan("Do something")

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

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Complex task")

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

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Question")

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

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        service.createPlan("Varieties.", history)

        val userMessageText = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertTrue(userMessageText.contains("Tomatoes are wonderful fruits..."))
        assertTrue(userMessageText.contains("Varieties."))
    }

    @Test
    fun `sends only user message when history is empty`() {
        val json = """{"reasoning": "Simple", "steps": [{"stepNumber": 1, "description": "Answer", "expectedTools": []}]}"""

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        service.createPlan("Varieties.", emptyList())

        val userMessageText = (capturedRequest!!.messages().last() as UserMessage).singleText()
        assertEquals("Varieties.", userMessageText)
    }

    @Test
    fun `planning prompt contains skill names and descriptions`() {
        val json = """{"reasoning": "Simple", "steps": [{"stepNumber": 1, "description": "Answer", "expectedTools": []}]}"""

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        service.createPlan("Hello")

        val systemMessageText = (capturedRequest!!.messages().first() as SystemMessage).text()
        assertTrue(systemMessageText.contains("general: General knowledge and conversation"))
        assertTrue(systemMessageText.contains("time: Time and timezone queries"))
        assertTrue(systemMessageText.contains("Available Skills"))
    }

    @Test
    fun `parsed plan preserves skillName field from JSON`() {
        val json = """
            {
              "reasoning": "Two skills needed",
              "steps": [
                {"stepNumber": 1, "description": "Garden stuff", "expectedTools": [], "skillName": "garden"},
                {"stepNumber": 2, "description": "Time stuff", "expectedTools": [], "skillName": "time"}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Tell me about tomatoes then the time")

        assertEquals(2, plan.steps.size)
        assertEquals("garden", plan.steps[0].skillName)
        assertEquals("time", plan.steps[1].skillName)
    }

    @Test
    fun `parsed plan has null skillName when not provided in JSON`() {
        val json = """
            {
              "reasoning": "No skill specified",
              "steps": [
                {"stepNumber": 1, "description": "Just answer", "expectedTools": []}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Simple question")

        assertEquals(1, plan.steps.size)
        assertNull(plan.steps[0].skillName)
    }

    @Test
    fun `parsed plan preserves dependsOn field from JSON`() {
        val json = """
            {
              "reasoning": "Independent steps",
              "steps": [
                {"stepNumber": 1, "description": "Get Tokyo time", "expectedTools": [], "dependsOn": []},
                {"stepNumber": 2, "description": "Get NYC time", "expectedTools": [], "dependsOn": []},
                {"stepNumber": 3, "description": "Compare times", "expectedTools": [], "dependsOn": [1, 2]}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Time difference between Tokyo and NYC")

        assertEquals(3, plan.steps.size)
        assertEquals(emptyList<Int>(), plan.steps[0].dependsOn)
        assertEquals(emptyList<Int>(), plan.steps[1].dependsOn)
        assertEquals(listOf(1, 2), plan.steps[2].dependsOn)
    }

    @Test
    fun `parsed plan has empty dependsOn when not provided in JSON`() {
        val json = """
            {
              "reasoning": "No deps",
              "steps": [
                {"stepNumber": 1, "description": "Do it", "expectedTools": []}
              ]
            }
        """.trimIndent()

        val service = TaskPlanningService(fakeChatModel(json), agentProperties, skillCacheService)
        val plan = service.createPlan("Simple")

        assertEquals(1, plan.steps.size)
        assertEquals(emptyList<Int>(), plan.steps[0].dependsOn)
    }
}
