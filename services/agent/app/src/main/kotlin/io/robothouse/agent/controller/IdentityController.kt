package io.robothouse.agent.controller

import io.robothouse.agent.entity.Identity
import io.robothouse.agent.model.UpdateIdentityRequest
import io.robothouse.agent.service.IdentityService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for reading and updating the singleton identity configuration
 * that provides a global agent personality.
 */
@RestController
@RequestMapping("/api/identity")
@Tag(name = "Identity", description = "Identity configuration endpoints")
@Validated
class IdentityController(
    private val identityService: IdentityService
) {

    @Operation(summary = "Get the identity configuration", description = "Returns the singleton identity configuration with the global system prompt")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Identity configuration returned successfully")
        ]
    )
    @GetMapping
    fun get(): ResponseEntity<Identity> {
        val identity = identityService.get()
        return ResponseEntity.ok(identity)
    }

    @Operation(summary = "Update the identity configuration", description = "Replaces the identity system prompt with the provided value")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Identity configuration updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PutMapping
    fun update(@RequestBody @Valid request: UpdateIdentityRequest): ResponseEntity<Identity> {
        val updated = identityService.update(request)
        return ResponseEntity.ok(updated)
    }
}
