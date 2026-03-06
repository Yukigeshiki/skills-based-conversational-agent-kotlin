package io.robothouse.agent.controller

import io.robothouse.agent.model.ChatRequest
import io.robothouse.agent.model.ChatResponse
import io.robothouse.agent.service.AgentAssistant
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Agent chat endpoints")
@Validated
class ChatController(private val agentAssistant: AgentAssistant) {

    @Operation(summary = "Send a chat message", description = "Sends a message to the agent and returns the response")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Chat response returned successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @PostMapping
    fun chat(@RequestBody @Valid request: ChatRequest): ResponseEntity<ChatResponse> {
        val response = agentAssistant.chat(request.message)
        return ResponseEntity.ok(ChatResponse(response))
    }
}
