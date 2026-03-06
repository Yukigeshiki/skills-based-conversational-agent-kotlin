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

    @Test
    fun `formats error observations with ERROR prefix`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "Trying tool",
            toolCalls = listOf(ToolCall("badTool", "{}")),
            observations = listOf(ToolObservation("badTool", "Something went wrong", error = true))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(scratchpad.contains("ERROR [badTool]: Something went wrong"))
        assertTrue(!scratchpad.contains("Observation [badTool]"))
    }

    @Test
    fun `includes decision guidance when errors present`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = null,
            toolCalls = listOf(ToolCall("failTool", "{}")),
            observations = listOf(ToolObservation("failTool", "failed", error = true))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(scratchpad.contains("## Decision guidance"))
        assertTrue(scratchpad.contains("Retrying with different arguments"))
    }

    @Test
    fun `omits decision guidance when no errors`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "All good",
            toolCalls = listOf(ToolCall("goodTool", "{}")),
            observations = listOf(ToolObservation("goodTool", "success"))
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(!scratchpad.contains("Decision guidance"))
    }

    @Test
    fun `mixed success and error observations formatted correctly`() {
        val memory = TaskMemory()
        memory.addIteration(AgentIteration(
            iterationNumber = 1,
            thought = "Running tools",
            toolCalls = listOf(
                ToolCall("goodTool", "{}"),
                ToolCall("badTool", "{}")
            ),
            observations = listOf(
                ToolObservation("goodTool", "ok"),
                ToolObservation("badTool", "failed", error = true)
            )
        ))

        val scratchpad = memory.toScratchpad()!!

        assertTrue(scratchpad.contains("Observation [goodTool]: ok"))
        assertTrue(scratchpad.contains("ERROR [badTool]: failed"))
        assertTrue(scratchpad.contains("## Decision guidance"))
    }
}
