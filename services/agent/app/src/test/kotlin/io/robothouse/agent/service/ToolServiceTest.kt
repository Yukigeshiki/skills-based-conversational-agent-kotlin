package io.robothouse.agent.service

import io.robothouse.agent.repository.ToolRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ToolServiceTest {

    private val toolRepository: ToolRepository = mock()
    private val service = ToolService(toolRepository)

    @Test
    fun `returns sorted tool names`() {
        whenever(toolRepository.getToolNames()).thenReturn(setOf("WebSearchTool", "DateTimeTool", "CalculatorTool"))

        val result = service.getToolNames()

        assertEquals(listOf("CalculatorTool", "DateTimeTool", "WebSearchTool"), result)
    }

    @Test
    fun `returns empty list when no tools registered`() {
        whenever(toolRepository.getToolNames()).thenReturn(emptySet())

        val result = service.getToolNames()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `returns single tool`() {
        whenever(toolRepository.getToolNames()).thenReturn(setOf("OnlyTool"))

        val result = service.getToolNames()

        assertEquals(listOf("OnlyTool"), result)
    }
}
