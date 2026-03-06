package io.robothouse.agent.model

import io.robothouse.agent.validation.MaxTokens
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SkillRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(max = 64, message = "Name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name must contain only alphanumeric characters, hyphens, or underscores")
    val name: String,

    @field:NotBlank(message = "Description must not be blank")
    @field:Size(max = 1024, message = "Description must not exceed 1024 characters")
    val description: String,

    @field:NotBlank(message = "System prompt must not be blank")
    @field:MaxTokens(500, message = "System prompt must not exceed 500 tokens")
    val systemPrompt: String,

    @field:NotEmpty(message = "Tool names must not be empty")
    @field:Size(max = 10, message = "Tool names must not exceed 10 entries")
    val toolNames: List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", message = "Tool name must be camelCase alphanumeric") String>,

    @field:MaxTokens(500, message = "Planning prompt must not exceed 500 tokens")
    val planningPrompt: String? = null
)
