package io.robothouse.agent.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StringMapConverterTest {

    private lateinit var converter: StringMapConverter

    @BeforeEach
    fun setUp() {
        converter = StringMapConverter()
    }

    @Test
    fun `converts map to JSON string`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")

        val result = converter.convertToDatabaseColumn(map)

        assertTrue(result.contains("key1"))
        assertTrue(result.contains("value1"))
        assertTrue(result.contains("key2"))
        assertTrue(result.contains("value2"))
    }

    @Test
    fun `converts JSON string back to map`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val json = converter.convertToDatabaseColumn(map)

        val result = converter.convertToEntityAttribute(json)

        assertEquals(map, result)
    }

    @Test
    fun `converts null to empty JSON object`() {
        val result = converter.convertToDatabaseColumn(null)

        assertEquals("{}", result)
    }

    @Test
    fun `converts blank string to empty map`() {
        val result = converter.convertToEntityAttribute("   ")

        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `round-trips empty map`() {
        val json = converter.convertToDatabaseColumn(emptyMap())

        assertEquals("{}", json)

        val result = converter.convertToEntityAttribute(json)

        assertEquals(emptyMap<String, String>(), result)
    }
}
