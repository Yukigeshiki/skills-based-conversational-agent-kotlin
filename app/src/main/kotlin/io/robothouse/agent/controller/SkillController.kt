package io.robothouse.agent.controller

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.SkillRequest
import io.robothouse.agent.service.SkillService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skills", description = "Skill management endpoints")
@Validated
class SkillController(
    private val skillService: SkillService
) {

    @Operation(summary = "Get all skills", description = "Returns a list of all registered skills")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skills returned successfully")
        ]
    )
    @GetMapping
    fun getAll(): ResponseEntity<List<Skill>> {
        val skills = skillService.findAll()
        return ResponseEntity.ok(skills)
    }

    @Operation(summary = "Get skill by ID", description = "Returns a single skill by its UUID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skill returned successfully"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<Skill> {
        val skill = skillService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(skill)
    }

    @Operation(summary = "Create a skill", description = "Creates a new skill with the provided configuration")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Skill created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PostMapping
    fun create(@RequestBody @Valid request: SkillRequest): ResponseEntity<Skill> {
        val saved = skillService.create(request)
        @Suppress("XSS") // All input values are validated via @Valid and custom validators
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @Operation(summary = "Update a skill", description = "Updates an existing skill by its UUID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skill updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: SkillRequest): ResponseEntity<Skill> {
        val updated = skillService.update(id, request) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }
}
