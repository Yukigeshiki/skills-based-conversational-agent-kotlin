package io.robothouse.agent.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class SkillRequest(
    @field:NotBlank(message = "Name must not be blank")
    val name: String,

    @field:NotBlank(message = "Description must not be blank")
    val description: String,

    @field:NotBlank(message = "System prompt must not be blank")
    val systemPrompt: String,

    @field:NotEmpty(message = "Tool names must not be empty")
    val toolNames: List<String>
)
