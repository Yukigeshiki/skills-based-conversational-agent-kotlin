package io.robothouse.agent.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskMemoryTest {

    @Test
    fun `toScratchpad returns null when empty`() {
        val memory = TaskMemory()
        assertNull(memory.toScratchpad())
    }

    @Test
    fun `formats single iteration with thought and tool calls`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "I need to check the time",
            toolCalls = listOf(ToolCall("getTime", "{\"tz\": \"UTC\"}")),
            observations = listOf(ToolObservation("getTime", "12:00 UTC"))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(scratchpad.contains("--- Iteration 1 ---"))
        assertTrue(scratchpad.contains("Thought: I need to check the time"))
        assertTrue(scratchpad.contains("Action: getTime({\"tz\": \"UTC\"})"))
        assertTrue(scratchpad.contains("Observation [getTime]: 12:00 UTC"))
    }

    @Test
    fun `formats multiple iterations`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "First step",
            toolCalls = listOf(ToolCall("toolA", "{}")),
            observations = listOf(ToolObservation("toolA", "result A"))
        ))
        memory.addIteration(AgentIteration(
            iterationNumber = 2,
            thought = "Second step",
            toolCalls = listOf(ToolCall("toolB", "{}")),
            observations = listOf(ToolObservation("toolB", "result B"))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(scratchpad.contains("--- Iteration 1 ---"))
        assertTrue(scratchpad.contains("--- Iteration 2 ---"))
        assertTrue(scratchpad.contains("Thought: First step"))
        assertTrue(scratchpad.contains("Thought: Second step"))
    }

    @Test
    fun `skips blank thoughts in output`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "",
            toolCalls = listOf(ToolCall("tool", "{}")),
            observations = listOf(ToolObservation("tool", "done"))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(!scratchpad.contains("Thought:"))
        assertTrue(scratchpad.contains("Action: tool({})"))
    }

    @Test
    fun `skips null thoughts in output`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = null,
            toolCalls = listOf(ToolCall("tool", "{}")),
            observations = listOf(ToolObservation("tool", "done"))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(!scratchpad.contains("Thought:"))
    }

    @Test
    fun `iterations returns immutable copy`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(iterationNumber = 1, thought = "test"))

        assertEquals(1, memory.iterations.size)
        assertEquals("test", memory.iterations[0].thought)
    }
}
