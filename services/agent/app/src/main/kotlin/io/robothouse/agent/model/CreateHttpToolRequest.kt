package io.robothouse.agent.model

import io.robothouse.agent.validator.SafeUrl
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for creating a new http tool backed by an HTTP endpoint.
 */
data class CreateHttpToolRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(max = 64, message = "Name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", message = "Name must be camelCase alphanumeric (e.g. weatherLookup)")
    val name: String,

    @field:NotBlank(message = "Description must not be blank")
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String,

    @field:NotBlank(message = "Endpoint URL must not be blank")
    @field:Size(max = 2048, message = "Endpoint URL must not exceed 2048 characters")
    @field:SafeUrl
    val endpointUrl: String,

    val httpMethod: HttpMethod,

    @field:Size(max = 20, message = "Headers must not exceed 20 entries")
    val headers: Map<String, String> = emptyMap(),

    @field:Size(max = 20, message = "Parameters must not exceed 20 entries")
    @field:Valid
    val parameters: List<HttpToolParameterRequest> = emptyList(),

    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 120, message = "Timeout must not exceed 120 seconds")
    val timeoutSeconds: Int,

    @field:Min(value = 100, message = "Max response length must be at least 100 characters")
    @field:Max(value = 16000, message = "Max response length must not exceed 16000 characters")
    val maxResponseLength: Int
)
