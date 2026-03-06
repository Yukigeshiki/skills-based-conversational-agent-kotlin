package io.robothouse.agent.model

import io.robothouse.agent.validator.MaxTokens
import io.robothouse.agent.validator.RegisteredTools
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for partially updating an existing skill.
 *
 * Only non-null fields are applied to the skill, following PATCH semantics.
 */
data class UpdateSkillRequest(
    @field:Size(max = 64, message = "Name must not exceed 64 characters")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Name must contain only alphanumeric characters, hyphens, or underscores")
    val name: String? = null,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:MaxTokens(500, message = "System prompt must not exceed 500 tokens")
    val systemPrompt: String? = null,

    @field:Size(max = 10, message = "Tool names must not exceed 10 entries")
    @field:RegisteredTools(rejectEmpty = true)
    val toolNames: List<@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", message = "Tool name must be camelCase alphanumeric") String>? = null,

    @field:MaxTokens(500, message = "Planning prompt must not exceed 500 tokens")
    val planningPrompt: String? = null
)
