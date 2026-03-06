package io.robothouse.agent.controller

import io.robothouse.agent.model.ChatRequest
import io.robothouse.agent.service.StreamingChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * REST controller for agent chat interactions.
 *
 * Routes incoming messages to the appropriate skill and streams agent
 * events as Server-Sent Events for real-time observability of skill
 * routing, planning, tool execution, and LLM reasoning.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Agent chat endpoints")
@Validated
class ChatController(
    private val streamingChatService: StreamingChatService
) {

    /**
     * Streams agent events as Server-Sent Events for real-time observability
     * of skill routing, planning, tool execution, and LLM reasoning.
     */
    @Operation(summary = "Stream a chat interaction via SSE", description = "Sends a message and streams agent events as Server-Sent Events")
    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody @Valid request: ChatRequest): SseEmitter {
        return streamingChatService.streamChat(request.message)
    }
}
