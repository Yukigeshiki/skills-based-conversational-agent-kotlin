package io.robothouse.agent.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attribute: List<String>?): String =
        attribute?.joinToString(",") ?: ""

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        dbData?.takeIf { it.isNotBlank() }?.split(",") ?: emptyList()
}
