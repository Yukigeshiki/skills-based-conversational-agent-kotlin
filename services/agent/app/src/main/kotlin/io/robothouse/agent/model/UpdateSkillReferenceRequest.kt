package io.robothouse.agent.model

import jakarta.validation.constraints.Size

/**
 * Request body for partially updating a skill reference. All fields are optional.
 */
data class UpdateSkillReferenceRequest(
    @field:Size(max = 255, message = "Name must not exceed 255 characters")
    val name: String? = null,

    val content: String? = null
)
