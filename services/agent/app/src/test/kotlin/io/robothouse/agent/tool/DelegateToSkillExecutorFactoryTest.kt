package io.robothouse.agent.tool

import dev.langchain4j.agent.tool.ToolExecutionRequest
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.listener.AgentEventListener
import io.robothouse.agent.model.AgentEvent
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.service.SkillCacheService
import io.robothouse.agent.service.SkillService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DelegateToSkillExecutorFactoryTest {

    private val skillService: SkillService = mock()
    private val skillCacheService: SkillCacheService = mock()
    private val factory = DelegateToSkillExecutorFactory(skillService, skillCacheService)

    private val targetSkill = Skill(
        name = "time-skill",
        description = "Time queries",
        systemPrompt = "You handle time.",
        toolNames = listOf("DateTimeTool")
    )

    @BeforeEach
    fun setUp() {
        whenever(skillCacheService.findAll()).thenReturn(
            listOf(
                Skill(name = "general-assistant", description = "General knowledge", systemPrompt = "...", toolNames = listOf("DateTimeTool")),
                Skill(name = "time-skill", description = "Time queries", systemPrompt = "...", toolNames = listOf("DateTimeTool"))
            )
        )
    }

    @Test
    fun `specification has correct name and parameters`() {
        val spec = factory.specification()

        assertEquals("delegateToSkill", spec.name())
        assertTrue(spec.description().contains("Delegates a request"))
        assertTrue(spec.description().contains("Available skills"))
        assertTrue(spec.description().contains("general-assistant"))
        assertTrue(spec.description().contains("time-skill"))
    }

    @Test
    fun `specification includes tool names for skills that have them`() {
        val spec = factory.specification()

        assertTrue(spec.description().contains("DateTimeTool"))
    }

    @Test
    fun `executor returns depth limit error when at max depth`() {
        val executor = factory.createExecutor(
            currentDepth = 2,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = AgentEventListener.NOOP,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> throw AssertionError("Should not be called") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"skillName": "time-skill", "request": "What time?"}""")
            .build()

        val result = executor.execute(request, null)

        assertTrue(result.contains("Delegation depth limit reached"))
        assertTrue(result.contains("max: 2"))
    }

    @Test
    fun `executor returns error for missing skill`() {
        whenever(skillService.findByName("nonexistent")).thenReturn(null)

        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = AgentEventListener.NOOP,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> throw AssertionError("Should not be called") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"skillName": "nonexistent", "request": "Hello"}""")
            .build()

        val result = executor.execute(request, null)

        assertTrue(result.contains("not found"))
        assertTrue(result.contains("nonexistent"))
    }

    @Test
    fun `executor returns error for invalid JSON arguments`() {
        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = AgentEventListener.NOOP,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> throw AssertionError("Should not be called") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("not valid json")
            .build()

        val result = executor.execute(request, null)

        assertTrue(result.contains("Invalid arguments"))
    }

    @Test
    fun `executor delegates successfully and returns response`() {
        whenever(skillService.findByName("time-skill")).thenReturn(targetSkill)

        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = AgentEventListener.NOOP,
            conversationId = "conv-123",
            delegateFn = { skill, req, depth, _, _ ->
                assertEquals("time-skill", skill.name)
                assertEquals("What time is it?", req)
                assertEquals(1, depth)
                AgentResponse(response = "It is 3pm in Tokyo.")
            }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"skillName": "time-skill", "request": "What time is it?"}""")
            .build()

        val result = executor.execute(request, null)

        assertEquals("It is 3pm in Tokyo.", result)
    }

    @Test
    fun `executor emits handoff started and completed events on success`() {
        whenever(skillService.findByName("time-skill")).thenReturn(targetSkill)

        val events = mutableListOf<AgentEvent>()
        val listener = AgentEventListener { events.add(it) }

        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = listener,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> AgentResponse(response = "Done") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"skillName": "time-skill", "request": "Hello"}""")
            .build()

        executor.execute(request, null)

        val started = events.filterIsInstance<AgentEvent.SkillHandoffStartedEvent>()
        assertEquals(1, started.size)
        assertEquals("test-skill", started[0].fromSkill)
        assertEquals("time-skill", started[0].toSkill)
        assertEquals("Hello", started[0].request)
        assertEquals(0, started[0].delegationDepth)

        val completed = events.filterIsInstance<AgentEvent.SkillHandoffCompletedEvent>()
        assertEquals(1, completed.size)
        assertEquals("test-skill", completed[0].fromSkill)
        assertEquals("time-skill", completed[0].toSkill)
        assertTrue(completed[0].success)
    }

    @Test
    fun `executor emits handoff completed with success false on delegation failure`() {
        whenever(skillService.findByName("time-skill")).thenReturn(targetSkill)

        val events = mutableListOf<AgentEvent>()
        val listener = AgentEventListener { events.add(it) }

        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = listener,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> throw RuntimeException("LLM unavailable") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"skillName": "time-skill", "request": "Hello"}""")
            .build()

        val result = executor.execute(request, null)

        assertTrue(result.contains("failed"))
        assertTrue(result.contains("LLM unavailable"))

        val completed = events.filterIsInstance<AgentEvent.SkillHandoffCompletedEvent>()
        assertEquals(1, completed.size)
        assertFalse(completed[0].success)
    }

    @Test
    fun `executor returns error for missing skillName parameter`() {
        val executor = factory.createExecutor(
            currentDepth = 0,
            maxDepth = 2,
            currentSkillName = "test-skill",
            listener = AgentEventListener.NOOP,
            conversationId = null,
            delegateFn = { _, _, _, _, _ -> throw AssertionError("Should not be called") }
        )

        val request = ToolExecutionRequest.builder()
            .name("delegateToSkill")
            .arguments("""{"request": "Hello"}""")
            .build()

        val result = executor.execute(request, null)

        assertTrue(result.contains("Missing required parameter: skillName"))
    }
}
