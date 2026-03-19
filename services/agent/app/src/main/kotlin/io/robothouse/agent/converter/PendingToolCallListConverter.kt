package io.robothouse.agent.converter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.robothouse.agent.model.PendingToolCall
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA converter for serializing `List<PendingToolCall>` to and from
 * a JSON string for storage in a PostgreSQL JSONB column.
 */
@Converter
class PendingToolCallListConverter : AttributeConverter<List<PendingToolCall>, String> {

    private val objectMapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: List<PendingToolCall>?): String {
        return objectMapper.writeValueAsString(attribute ?: emptyList<PendingToolCall>())
    }

    override fun convertToEntityAttribute(dbData: String?): List<PendingToolCall> {
        if (dbData.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(dbData)
    }
}
