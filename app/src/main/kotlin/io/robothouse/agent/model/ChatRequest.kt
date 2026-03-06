package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank

data class ChatRequest(
    @field:NotBlank(message = "Message must not be blank")
    val message: String
)
