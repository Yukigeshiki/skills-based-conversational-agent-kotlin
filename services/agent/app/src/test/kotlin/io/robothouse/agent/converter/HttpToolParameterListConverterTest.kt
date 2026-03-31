package io.robothouse.agent.converter

import io.robothouse.agent.model.HttpToolParameter
import io.robothouse.agent.model.ParameterType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HttpToolParameterListConverterTest {

    private lateinit var converter: HttpToolParameterListConverter

    @BeforeEach
    fun setUp() {
        converter = HttpToolParameterListConverter()
    }

    @Test
    fun `converts parameter list to JSON string`() {
        val params = listOf(
            HttpToolParameter(name = "city", type = ParameterType.STRING, description = "City name", required = true)
        )

        val result = converter.convertToDatabaseColumn(params)

        assertTrue(result.contains("city"))
        assertTrue(result.contains("City name"))
    }

    @Test
    fun `converts JSON string back to parameter list`() {
        val params = listOf(
            HttpToolParameter(name = "city", type = ParameterType.STRING, description = "City name", required = true),
            HttpToolParameter(name = "units", type = ParameterType.STRING, description = "Temperature units", required = false)
        )
        val json = converter.convertToDatabaseColumn(params)

        val result = converter.convertToEntityAttribute(json)

        assertEquals(params, result)
    }

    @Test
    fun `converts null to empty JSON array`() {
        val result = converter.convertToDatabaseColumn(null)

        assertEquals("[]", result)
    }

    @Test
    fun `converts blank string to empty list`() {
        val result = converter.convertToEntityAttribute("   ")

        assertEquals(emptyList<HttpToolParameter>(), result)
    }

    @Test
    fun `round-trips parameter with enum type`() {
        val params = listOf(
            HttpToolParameter(name = "count", type = ParameterType.STRING, description = "Item count", required = true)
        )
        val json = converter.convertToDatabaseColumn(params)

        assertTrue(json.contains("\"string\""))

        val result = converter.convertToEntityAttribute(json)

        assertEquals(ParameterType.STRING, result[0].type)
    }
}
