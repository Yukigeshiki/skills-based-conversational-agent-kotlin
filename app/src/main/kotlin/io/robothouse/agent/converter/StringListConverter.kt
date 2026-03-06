package io.robothouse.agent.converter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA attribute converter that stores a list of strings as a JSON array
 * in a database column.
 */
@Converter
class StringListConverter : AttributeConverter<List<String>, String> {

    private val objectMapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: List<String>?): String =
        objectMapper.writeValueAsString(attribute ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList()
        else try {
            objectMapper.readValue(dbData)
        } catch (_: Exception) {
            // Backwards compatibility: handle legacy comma-separated format
            dbData.split(",").filter { it.isNotBlank() }
        }
}
