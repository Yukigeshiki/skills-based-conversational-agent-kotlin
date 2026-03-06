package io.robothouse.agent.service

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.SkillRequest
import io.robothouse.agent.repository.SkillRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SkillService(
    private val skillRepository: SkillRepository,
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>
) {

    fun findAll(): List<Skill> = skillRepository.findAll()

    fun findById(id: UUID): Skill? = skillRepository.findById(id).orElse(null)

    fun create(request: SkillRequest): Skill {
        val skill = Skill(
            name = request.name,
            description = request.description,
            systemPrompt = request.systemPrompt,
            toolNames = request.toolNames,
            planningPrompt = request.planningPrompt
        )
        val saved = skillRepository.save(skill)
        embedSkill(saved)
        return saved
    }

    fun update(id: UUID, request: SkillRequest): Skill? {
        val existing = skillRepository.findById(id).orElse(null) ?: return null
        existing.name = request.name
        existing.description = request.description
        existing.systemPrompt = request.systemPrompt
        existing.toolNames = request.toolNames
        existing.planningPrompt = request.planningPrompt
        val saved = skillRepository.save(existing)
        embedSkill(saved)
        return saved
    }

    private fun embedSkill(skill: Skill) {
        val embedding = embeddingModel.embed(skill.description).content()
        val segment = TextSegment.from(skill.description, Metadata.from("skillId", skill.id.toString()))
        embeddingStore.add(embedding, segment)
    }
}
