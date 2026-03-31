package io.robothouse.agent.converter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for serializing `Map<String, String>` to and from
 * a JSON string for storage in a PostgreSQL JSONB column.
 */
@Converter
class StringMapConverter : AttributeConverter<Map<String, String>, String> {

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }

    /**
     * Serializes the map to a JSON string, defaulting to an empty object
     * for null input.
     */
    override fun convertToDatabaseColumn(attribute: Map<String, String>?): String {
        return objectMapper.writeValueAsString(attribute ?: emptyMap<String, String>())
    }

    /**
     * Deserializes a JSON string back into a map, returning an empty map
     * for null or blank input.
     */
    override fun convertToEntityAttribute(dbData: String?): Map<String, String> {
        if (dbData.isNullOrBlank()) return emptyMap()
        return objectMapper.readValue(dbData)
    }
}
