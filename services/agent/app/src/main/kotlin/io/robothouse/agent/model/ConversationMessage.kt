package io.robothouse.agent.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class ConversationMessage(
    val role: String,
    val content: String,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val activities: List<AgentEvent> = emptyList(),
    val timestamp: Instant = Instant.now()
)
