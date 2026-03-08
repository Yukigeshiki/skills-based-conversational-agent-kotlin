package io.robothouse.agent.service

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.SkillRequest
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.repository.SkillRepository
import io.robothouse.agent.util.log
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Service layer for skill CRUD operations.
 *
 * Manages skill persistence and maintains embedding store consistency
 * by updating embeddings whenever a skill's description changes.
 */
@Service
class SkillService(
    private val skillRepository: SkillRepository,
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Returns a paged result of skills, optionally filtered by search term and/or tool names.
     */
    fun findAllPaged(search: String? = null, tools: List<String>? = null, pageable: Pageable): Page<Skill> {
        log.debug { "Fetching paged skills: search=$search, tools=$tools, page=${pageable.pageNumber}, size=${pageable.pageSize}" }
        if (search != null || !tools.isNullOrEmpty()) {
            return skillRepository.findAllFilteredPaged(search, tools, pageable)
        }
        return skillRepository.findAll(pageable)
    }

    /**
     * Returns the skill with the given ID.
     */
    fun findById(id: UUID): Skill {
        log.debug { "Retrieving skill: id=$id" }
        return skillRepository.findById(id).orElseThrow {
            log.warn { "Attempt to access non-existent skill: id=$id" }
            NotFoundException("Skill not found")
        }
    }

    /**
     * Creates a new skill and stores its description embedding for similarity routing.
     */
    fun create(request: SkillRequest): Skill {
        log.debug { "Processing create request for skill: name=${request.name}" }

        val saved = transactionTemplate.execute {
            if (skillRepository.findByName(request.name) != null) {
                throw BadRequestException("A skill with name '${request.name}' already exists")
            }

            val skill = Skill(
                name = request.name,
                description = request.description,
                systemPrompt = request.systemPrompt,
                toolNames = request.toolNames,
                planningPrompt = request.planningPrompt
            )
            val persisted = skillRepository.save(skill)
            embedSkill(persisted)
            persisted
        }!!

        log.info { "Created skill: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Partially updates the skill with the given ID, applying only non-null fields.
     *
     * Re-embeds the skill description if it was changed.
     */
    fun update(id: UUID, request: UpdateSkillRequest): Skill {
        log.debug { "Processing update request for skill: id=$id" }

        val saved = transactionTemplate.execute {
            // Step 1: Execute partial update
            val persisted = skillRepository.patchUpdate(id, request)
                ?: run {
                    log.warn { "Attempt to update non-existent skill: id=$id" }
                    throw NotFoundException("Skill not found")
                }

            // Step 2: Update embedding if description changed
            request.description?.let {
                removeEmbeddingsBySkillId(persisted.id.toString())
                embedSkill(persisted)
            }

            persisted
        }!!

        log.info { "Updated skill: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Deletes the skill with the given ID and removes its embedding from the store.
     */
    fun delete(id: UUID) {
        log.debug { "Processing delete request for skill: id=$id" }

        transactionTemplate.execute {
            if (!skillRepository.existsById(id)) {
                log.warn { "Attempt to delete non-existent skill: id=$id" }
                throw NotFoundException("Skill not found")
            }

            removeEmbeddingsBySkillId(id.toString())
            skillRepository.deleteById(id)
        }

        log.info { "Deleted skill: id=$id" }
    }

    /**
     * Embeds the skill's description and stores it in the embedding store
     * with the skill ID and a description hash in the metadata.
     */
    private fun embedSkill(skill: Skill) {
        val embedding = embeddingModel.embed(skill.description).content()
        val descriptionHash = skill.description.hashCode().toString()
        val metadata = Metadata.from("skillId", skill.id.toString()).put("descriptionHash", descriptionHash)
        val segment = TextSegment.from(skill.description, metadata)
        embeddingStore.add(embedding, segment)
    }

    /**
     * Removes all embeddings associated with the given skill ID from the embedding store.
     */
    private fun removeEmbeddingsBySkillId(skillId: String) {
        embeddingStore.removeAll(metadataKey("skillId").isEqualTo(skillId))
    }
}