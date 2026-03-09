package io.robothouse.agent.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun `converts list to JSON array`() {
        val result = converter.convertToDatabaseColumn(listOf("tool1", "tool2"))

        assertEquals("""["tool1","tool2"]""", result)
    }

    @Test
    fun `converts empty list to empty JSON array`() {
        val result = converter.convertToDatabaseColumn(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `converts null to empty JSON array`() {
        val result = converter.convertToDatabaseColumn(null)

        assertEquals("[]", result)
    }

    @Test
    fun `converts JSON array to list`() {
        val result = converter.convertToEntityAttribute("""["tool1","tool2"]""")

        assertEquals(listOf("tool1", "tool2"), result)
    }

    @Test
    fun `converts empty JSON array to empty list`() {
        val result = converter.convertToEntityAttribute("[]")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `returns empty list for null`() {
        val result = converter.convertToEntityAttribute(null)

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `returns empty list for blank string`() {
        val result = converter.convertToEntityAttribute("   ")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `returns empty list for empty string`() {
        val result = converter.convertToEntityAttribute("")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `falls back to comma-separated parsing for legacy format`() {
        val result = converter.convertToEntityAttribute("tool1,tool2,tool3")

        assertEquals(listOf("tool1", "tool2", "tool3"), result)
    }

    @Test
    fun `legacy comma-separated parsing filters blank entries`() {
        val result = converter.convertToEntityAttribute("tool1,,tool2, ,tool3")

        assertEquals(listOf("tool1", "tool2", "tool3"), result)
    }

    @Test
    fun `handles single-element JSON array`() {
        val result = converter.convertToEntityAttribute("""["onlyTool"]""")

        assertEquals(listOf("onlyTool"), result)
    }

    @Test
    fun `handles strings with special characters in JSON`() {
        val result = converter.convertToDatabaseColumn(listOf("tool-one", "tool_two"))

        assertEquals("""["tool-one","tool_two"]""", result)
    }

    @Test
    fun `round-trips through conversion`() {
        val original = listOf("DateTimeTool", "WebSearchTool", "CalculatorTool")
        val json = converter.convertToDatabaseColumn(original)
        val restored = converter.convertToEntityAttribute(json)

        assertEquals(original, restored)
    }
}
