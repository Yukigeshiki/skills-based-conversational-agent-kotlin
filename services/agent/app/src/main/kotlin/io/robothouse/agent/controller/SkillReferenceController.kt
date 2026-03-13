package io.robothouse.agent.controller

import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.model.CreateSkillReferenceRequest
import io.robothouse.agent.model.UpdateSkillReferenceRequest
import io.robothouse.agent.service.SkillReferenceService
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
 * REST controller for managing reference documents attached to skills.
 */
@RestController
@RequestMapping("/api/skills/{skillId}/references")
@CrossOrigin(originPatterns = ["http://localhost:[*]", "http://127.0.0.1:[*]"], allowCredentials = "true")
@Tag(name = "Skill References", description = "Skill reference document management endpoints")
@Validated
class SkillReferenceController(
    private val skillReferenceService: SkillReferenceService
) {

    @Operation(summary = "Create a skill reference", description = "Creates a new reference document for a skill.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Reference created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @PostMapping
    fun create(
        @PathVariable skillId: UUID,
        @RequestBody @Valid request: CreateSkillReferenceRequest
    ): ResponseEntity<SkillReference> {
        val saved = skillReferenceService.create(skillId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @Operation(summary = "Get all references for a skill", description = "Returns all references for a skill.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "References returned successfully"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @GetMapping
    fun getAll(@PathVariable skillId: UUID): ResponseEntity<List<SkillReference>> {
        val references = skillReferenceService.findBySkillId(skillId)
        return ResponseEntity.ok(references)
    }

    @Operation(summary = "Get a reference by ID", description = "Returns a single reference by its UUID.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Reference returned successfully"),
            ApiResponse(responseCode = "404", description = "Reference or skill not found")
        ]
    )
    @GetMapping("/{referenceId}")
    fun getById(
        @PathVariable skillId: UUID,
        @PathVariable referenceId: UUID
    ): ResponseEntity<SkillReference> {
        val reference = skillReferenceService.findById(skillId, referenceId)
        return ResponseEntity.ok(reference)
    }

    @Operation(summary = "Update a reference", description = "Partially updates an existing reference by its UUID.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Reference updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Reference or skill not found")
        ]
    )
    @PatchMapping("/{referenceId}")
    fun update(
        @PathVariable skillId: UUID,
        @PathVariable referenceId: UUID,
        @RequestBody @Valid request: UpdateSkillReferenceRequest
    ): ResponseEntity<SkillReference> {
        val updated = skillReferenceService.update(skillId, referenceId, request)
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "Delete a reference", description = "Deletes a reference and its embeddings by UUID.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Reference deleted successfully"),
            ApiResponse(responseCode = "404", description = "Reference or skill not found")
        ]
    )
    @DeleteMapping("/{referenceId}")
    fun delete(
        @PathVariable skillId: UUID,
        @PathVariable referenceId: UUID
    ): ResponseEntity<Void> {
        skillReferenceService.delete(skillId, referenceId)
        return ResponseEntity.noContent().build()
    }
}
