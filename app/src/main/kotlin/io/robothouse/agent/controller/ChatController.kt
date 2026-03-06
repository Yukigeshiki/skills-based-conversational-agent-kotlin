package io.robothouse.agent.controller

import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ChatRequest
import io.robothouse.agent.service.DynamicAgentService
import io.robothouse.agent.service.SkillRouterService
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

/**
 * REST controller for agent chat interactions.
 *
 * Routes incoming messages to the appropriate skill via embedding similarity
 * and delegates execution to the dynamic agent service.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Agent chat endpoints")
@Validated
class ChatController(
    private val skillRouterService: SkillRouterService,
    private val dynamicAgentService: DynamicAgentService
) {

    @Operation(summary = "Send a chat message", description = "Sends a message to the agent and returns the response")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Chat response returned successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "500", description = "Internal server error")
        ]
    )
    @PostMapping
    fun chat(@RequestBody @Valid request: ChatRequest): ResponseEntity<AgentResponse> {
        val skill = skillRouterService.route(request.message)
        val response = dynamicAgentService.chat(skill, request.message)
        return ResponseEntity.ok(response)
    }
}
