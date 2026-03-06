package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank

/**
 * Request body for sending a chat message to the agent.
 */
data class ChatRequest(
    @field:NotBlank(message = "Message must not be blank")
    val message: String
)
