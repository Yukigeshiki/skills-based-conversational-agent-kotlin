package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for creating a new skill reference document.
 */
data class CreateSkillReferenceRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String,

    @field:NotBlank(message = "Content must not be blank")
    val content: String
)
