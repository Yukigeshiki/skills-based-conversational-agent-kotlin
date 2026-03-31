package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for defining a single parameter of an HTTP tool.
 */
data class HttpToolParameterRequest(
    @field:NotBlank(message = "Parameter name must not be blank")
    @field:Size(max = 64, message = "Parameter name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "Parameter name must start with a letter and contain only alphanumeric characters or underscores")
    val name: String,

    @field:NotNull(message = "Parameter type must not be null")
    var type: ParameterType,

    @field:NotBlank(message = "Parameter description must not be blank")
    @field:Size(max = 500, message = "Parameter description must not exceed 500 characters")
    val description: String,

    val required: Boolean
)
