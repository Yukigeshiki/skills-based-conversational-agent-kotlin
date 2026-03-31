package io.robothouse.agent.model

import com.fasterxml.jackson.annotation.JsonValue

/**
 * Defines a single parameter that an HTTP tool accepts.
 *
 * Used both as the JPA-persisted model (serialized to JSONB) and as the
 * source for building LangChain4j ToolSpecification parameter schemas.
 */
data class HttpToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean
)

/**
 * The supported JSON schema types for HTTP tool parameters.
 */
enum class ParameterType(@get:JsonValue val value: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean")
}
