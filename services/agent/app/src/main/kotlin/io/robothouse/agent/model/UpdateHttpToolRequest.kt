package io.robothouse.agent.model

import io.robothouse.agent.validator.NotAllNull
import io.robothouse.agent.validator.SafeUrl
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for partially updating an HTTP tool.
 *
 * Only non-null fields are applied, following PATCH semantics.
 * At least one field must be provided.
 */
@NotAllNull
data class UpdateHttpToolRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(max = 64, message = "Name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", message = "Name must be camelCase alphanumeric (e.g. weatherLookup)")
    val name: String? = null,

    @field:NotBlank(message = "Description must not be blank")
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:NotBlank(message = "Endpoint URL must not be blank")
    @field:Size(max = 2048, message = "Endpoint URL must not exceed 2048 characters")
    @field:SafeUrl
    val endpointUrl: String? = null,

    val httpMethod: HttpMethod? = null,

    @field:Size(max = 20, message = "Headers must not exceed 20 entries")
    val headers: Map<String, String>? = null,

    @field:Size(max = 20, message = "Parameters must not exceed 20 entries")
    @field:Valid
    val parameters: List<HttpToolParameterRequest>? = null,

    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 120, message = "Timeout must not exceed 120 seconds")
    val timeoutSeconds: Int? = null,

    @field:Min(value = 100, message = "Max response length must be at least 100 characters")
    @field:Max(value = 16000, message = "Max response length must not exceed 16000 characters")
    val maxResponseLength: Int? = null
)
