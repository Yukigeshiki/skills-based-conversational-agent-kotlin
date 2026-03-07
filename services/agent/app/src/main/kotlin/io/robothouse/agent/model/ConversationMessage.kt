package io.robothouse.agent.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * A single message in a conversation persisted to Redis.
 *
 * Captures the role, content, and timestamp for LLM context injection.
 * Assistant messages additionally carry the activity events that occurred
 * during their generation, enabling the UI to display them after page refresh.
 */
data class ConversationMessage(
    val role: String,
    val content: String,
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val activities: List<AgentEvent> = emptyList(),
    val timestamp: Instant = Instant.now()
)
