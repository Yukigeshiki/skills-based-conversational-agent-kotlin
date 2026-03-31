package io.robothouse.agent.controller

import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.model.CreateHttpToolRequest
import io.robothouse.agent.model.TestHttpToolRequest
import io.robothouse.agent.model.TestHttpToolResponse
import io.robothouse.agent.model.UpdateHttpToolRequest
import io.robothouse.agent.service.HttpToolService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for CRUD operations on HTTP tools.
 *
 * Supports listing, retrieving, creating, updating, deleting,
 * and testing user-defined HTTP tools.
 */
@RestController
@RequestMapping("/api/http-tools")
@CrossOrigin(originPatterns = ["http://localhost:[*]", "http://127.0.0.1:[*]"], allowCredentials = "true")
@Tag(name = "HTTP Tools", description = "HTTP tool management endpoints")
@Validated
class HttpToolController(
    private val httpToolService: HttpToolService
) {

    @Operation(summary = "Get all HTTP tools", description = "Returns all user-defined HTTP tools")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "HTTP tools returned successfully")
        ]
    )
    @GetMapping
    fun getAll(): ResponseEntity<List<HttpTool>> {
        val tools = httpToolService.findAll()
        return ResponseEntity.ok(tools)
    }

    @Operation(summary = "Get HTTP tool by ID", description = "Returns a single HTTP tool by its UUID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "HTTP tool returned successfully"),
            ApiResponse(responseCode = "404", description = "HTTP tool not found")
        ]
    )
    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<HttpTool> {
        val tool = httpToolService.findById(id)
        return ResponseEntity.ok(tool)
    }

    @Operation(summary = "Create an HTTP tool", description = "Creates a new user-defined HTTP tool with the provided configuration")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "HTTP tool created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "409", description = "Tool name already exists or conflicts with a built-in tool")
        ]
    )
    @PostMapping
    fun create(@RequestBody @Valid request: CreateHttpToolRequest): ResponseEntity<HttpTool> {
        val saved = httpToolService.create(request)
        @Suppress("XSS") // False positive — response is JSON-serialized, not rendered as HTML
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @Operation(summary = "Update an HTTP tool", description = "Partially updates an existing HTTP tool by its UUID. Only provided fields are updated.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "HTTP tool updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "HTTP tool not found"),
            ApiResponse(responseCode = "409", description = "Tool name already exists or conflicts with a built-in tool")
        ]
    )
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: UpdateHttpToolRequest): ResponseEntity<HttpTool> {
        val updated = httpToolService.update(id, request)
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "Delete an HTTP tool", description = "Deletes an HTTP tool by its UUID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "HTTP tool deleted successfully"),
            ApiResponse(responseCode = "404", description = "HTTP tool not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        httpToolService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Test an HTTP tool", description = "Executes an HTTP tool with sample arguments and returns the HTTP response")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Test executed successfully"),
            ApiResponse(responseCode = "404", description = "HTTP tool not found")
        ]
    )
    @PostMapping("/{id}/test")
    fun testTool(@PathVariable id: UUID, @RequestBody @Valid request: TestHttpToolRequest): ResponseEntity<TestHttpToolResponse> {
        val result = httpToolService.testTool(id, request)
        return ResponseEntity.ok(result)
    }
}
