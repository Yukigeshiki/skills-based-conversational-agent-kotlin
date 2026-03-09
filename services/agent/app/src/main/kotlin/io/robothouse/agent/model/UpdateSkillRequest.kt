package io.robothouse.agent.model

import io.robothouse.agent.validator.MaxTokens
import io.robothouse.agent.validator.RegisteredTools
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for partially updating an existing skill.
 *
 * Only non-null fields are applied to the skill, following PATCH semantics.
 */
data class UpdateSkillRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(max = 64, message = "Name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name must contain only alphanumeric characters, hyphens, or underscores")
    val name: String? = null,

    @field:NotBlank(message = "Description must not be blank")
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:NotBlank(message = "System prompt must not be blank")
    @field:MaxTokens(1000, message = "System prompt must not exceed 1000 tokens")
    val systemPrompt: String? = null,

    @field:Size(max = 10, message = "Tool names must not exceed 10 entries")
    @field:RegisteredTools
    val toolNames: List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", message = "Tool name must be camelCase alphanumeric") String> = emptyList()
)
