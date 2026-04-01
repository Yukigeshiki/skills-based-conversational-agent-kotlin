package io.robothouse.agent.model

import io.robothouse.agent.validator.MaxTokens
import jakarta.validation.constraints.Size

/** Request body for updating the identity system prompt. */
data class UpdateIdentityRequest(
    @field:Size(min = 10, message = "System prompt must be at least 10 characters")
    @field:MaxTokens(1000, message = "System prompt must not exceed 1000 tokens")
    var systemPrompt: String
)
