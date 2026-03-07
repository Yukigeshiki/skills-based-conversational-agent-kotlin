package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request body for sending a chat message to the agent.
 */
data class ChatRequest(
    @field:NotBlank(message = "Message must not be blank")
    val message: String,

    @field:Pattern(regexp = "^[a-f0-9\\-]{36}$", message = "conversationId must be a valid UUID")
    val conversationId: String? = null
)
