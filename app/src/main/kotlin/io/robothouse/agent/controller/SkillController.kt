package io.robothouse.agent.controller

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.SkillRequest
import io.robothouse.agent.repository.SkillRepository
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
class SkillController(
    private val skillRepository: SkillRepository,
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>
) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Skill>> =
        ResponseEntity.ok(skillRepository.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<Skill> =
        skillRepository.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.notFound().build())

    @PostMapping
    fun create(@RequestBody @Valid request: SkillRequest): ResponseEntity<Skill> {
        val skill = Skill(
            name = request.name,
            description = request.description,
            systemPrompt = request.systemPrompt,
            toolNames = request.toolNames
        )
        val saved = skillRepository.save(skill)
        embedSkill(saved)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody @Valid request: SkillRequest): ResponseEntity<Skill> {
        return skillRepository.findById(id)
            .map { existing ->
                existing.name = request.name
                existing.description = request.description
                existing.systemPrompt = request.systemPrompt
                existing.toolNames = request.toolNames
                val saved = skillRepository.save(existing)
                embedSkill(saved)
                ResponseEntity.ok(saved)
            }
            .orElse(ResponseEntity.notFound().build())
    }

    private fun embedSkill(skill: Skill) {
        val embedding = embeddingModel.embed(skill.description).content()
        val segment = TextSegment.from(skill.description, Metadata.from("skillId", skill.id.toString()))
        embeddingStore.add(embedding, segment)
    }
}
