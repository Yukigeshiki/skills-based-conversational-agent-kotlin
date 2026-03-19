package io.robothouse.agent.controller

import io.robothouse.agent.model.ApprovalRequest
import io.robothouse.agent.model.ChatRequest
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.service.ConversationMemoryService
import io.robothouse.agent.service.PendingApprovalService
import io.robothouse.agent.service.StreamingChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
 * routing, planning, tool execution, and LLM reasoning. Supports
 * conversation history via Redis-backed memory.
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(originPatterns = ["http://localhost:[*]", "http://127.0.0.1:[*]"], allowCredentials = "true")
@Tag(name = "Chat", description = "Agent chat endpoints")
@Validated
class ChatController(
    private val streamingChatService: StreamingChatService,
    private val conversationMemoryService: ConversationMemoryService,
    private val pendingApprovalService: PendingApprovalService
) {

    @Operation(summary = "Stream a chat interaction via SSE", description = "Sends a message and streams agent events as Server-Sent Events")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "SSE stream started successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@RequestBody @Valid request: ChatRequest): SseEmitter {
        return streamingChatService.streamChat(request.message, request.conversationId)
    }

    @Operation(summary = "Get conversation history", description = "Returns the message history for a conversation by its ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Conversation history returned successfully")
        ]
    )
    @GetMapping("/{conversationId}/history")
    fun getHistory(@PathVariable @Pattern(regexp = "^[a-f0-9\\-]{36}$") conversationId: String): List<ConversationMessage> {
        return conversationMemoryService.getHistory(conversationId)
    }

    @Operation(summary = "Approve or reject pending tool execution", description = "Resolves a pending tool approval and streams the remaining agent events as SSE")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Approval resolved, SSE stream started"),
            ApiResponse(responseCode = "404", description = "No pending approval found"),
            ApiResponse(responseCode = "409", description = "Approval already resolved")
        ]
    )
    @PostMapping("/{conversationId}/approve", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun approve(
        @PathVariable conversationId: String,
        @RequestBody @Valid request: ApprovalRequest
    ): SseEmitter {
        val approval = pendingApprovalService.resolve(request)
        return streamingChatService.resumeAfterApproval(approval, request.decision)
    }
}
