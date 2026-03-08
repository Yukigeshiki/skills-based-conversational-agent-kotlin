package io.robothouse.agent.controller

import io.robothouse.agent.service.ToolService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for tool discovery operations.
 */
@RestController
@RequestMapping("/api/tools")
@CrossOrigin(originPatterns = ["http://localhost:[*]", "http://127.0.0.1:[*]"], allowCredentials = "true")
@Tag(name = "Tools", description = "Tool discovery endpoints")
class ToolController(
    private val toolService: ToolService
) {

    @Operation(summary = "Get available tool names", description = "Returns the names of all registered tool beans")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Tool names returned successfully")
        ]
    )
    @GetMapping
    fun getToolNames(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(toolService.getToolNames())
    }
}
