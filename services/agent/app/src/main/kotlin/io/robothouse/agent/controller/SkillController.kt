package io.robothouse.agent.controller

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.GetPagedResponse
import io.robothouse.agent.model.CreateSkillRequest
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.service.SkillService
import io.robothouse.agent.validator.ValidSortParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for CRUD operations on skills.
 *
 * Supports listing, retrieving, creating, and partially updating skills.
 */
@RestController
@RequestMapping("/api/skills")
@Tag(name = "Skills", description = "Skill management endpoints")
@Validated
class SkillController(
    private val skillService: SkillService
) {

    @Operation(
        summary = "Get all skills",
        description = """
            Returns a paginated list of skills with optional filtering and sorting.

            Examples:
            - GET /api/skills - Returns first page of skills (default sort: createdAt desc)
            - GET /api/skills?page=0&size=20&sort=createdAt,desc - Explicit pagination
            - GET /api/skills?search=greeting - Filter by name or description
            - GET /api/skills?tools=DateTimeTool&tools=WebSearchTool - Filter by tools (OR logic)
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skills returned successfully"),
            ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters")
        ]
    )
    @GetMapping
    fun getAll(
        @Parameter(
            description = "Search term to filter by name or description.",
            example = "greeting"
        )
        @RequestParam(required = false)
        @Size(min = 1, max = 100, message = "Search term must be between 1 and 100 characters")
        search: String?,
        @Parameter(
            description = "Filter by tool names. Returns skills that use any of the specified tools (OR logic).",
            example = "DateTimeTool"
        )
        @RequestParam(required = false)
        tools: List<String>?,
        @Parameter(description = "Zero-based page index.", example = "0")
        @RequestParam(defaultValue = "0")
        @Min(0) @Max(10000)
        page: Int,
        @Parameter(description = "Page size.", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100)
        size: Int,
        @Parameter(description = "Sort property and direction (e.g. 'createdAt,desc').", example = "createdAt,desc")
        @RequestParam(defaultValue = "createdAt,desc")
        @ValidSortParam(entity = Skill::class)
        sort: String
    ): ResponseEntity<GetPagedResponse<Skill>> {
        val parts = sort.split(",", limit = 2)
        val property = parts[0]
        val direction = if (parts.size > 1 && parts[1].equals("asc", ignoreCase = true))
            Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, size, Sort.by(direction, property))

        val pagedResult = skillService.findAllPaged(search = search, tools = tools, pageable = pageable)
        return ResponseEntity.ok(GetPagedResponse.from(pagedResult))
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
        val skill = skillService.findById(id)
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
    fun create(@RequestBody @Valid request: CreateSkillRequest): ResponseEntity<Skill> {
        val saved = skillService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @Operation(summary = "Update a skill", description = "Partially updates an existing skill by its UUID. Only provided fields are updated.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skill updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: UpdateSkillRequest): ResponseEntity<Skill> {
        val updated = skillService.update(id, request)
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "Delete a skill", description = "Deletes a skill and its embedding by UUID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Skill deleted successfully"),
            ApiResponse(responseCode = "404", description = "Skill not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        skillService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
