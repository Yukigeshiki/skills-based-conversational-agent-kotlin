package io.robothouse.agent.converter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.robothouse.agent.model.HttpToolParameter
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for serializing `List<HttpToolParameter>` to and from
 * a JSON string for storage in a PostgreSQL JSONB column.
 */
@Converter
class HttpToolParameterListConverter : AttributeConverter<List<HttpToolParameter>, String> {

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }

    /**
     * Serializes the parameter list to a JSON string, defaulting to an
     * empty array for null input.
     */
    override fun convertToDatabaseColumn(attribute: List<HttpToolParameter>?): String {
        return objectMapper.writeValueAsString(attribute ?: emptyList<HttpToolParameter>())
    }

    /**
     * Deserializes a JSON string back into a parameter list, returning
     * an empty list for null or blank input.
     */
    override fun convertToEntityAttribute(dbData: String?): List<HttpToolParameter> {
        if (dbData.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(dbData)
    }
}
