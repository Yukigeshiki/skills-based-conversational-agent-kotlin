package io.robothouse.agent.service

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.ConflictException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.CreateSkillRequest
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.repository.SkillRepository
import io.robothouse.agent.util.log
import java.security.MessageDigest
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
    private val transactionTemplate: TransactionTemplate,
    private val skillCacheService: SkillCacheService
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
     * Returns the skill with the given name, or `null` if no match exists.
     */
    fun findByName(name: String): Skill? {
        log.debug { "Retrieving skill by name: $name" }
        return skillRepository.findByName(name)
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
     *
     * The embedding is stored inside the transaction so that an embedding failure
     * rolls back the DB save. The embedding store uses its own connection, so in the
     * rare case that the DB commit fails after the embedding succeeds, the orphaned
     * embedding is harmless and will be cleaned up by reconciliation on next startup.
     */
    fun create(request: CreateSkillRequest): Skill {
        log.debug { "Processing create request for skill: name=${request.name}" }

        val saved = transactionTemplate.execute {
            if (skillRepository.findByName(request.name) != null) {
                throw ConflictException("A skill with name '${request.name}' already exists")
            }

            val skill = Skill(
                name = request.name,
                description = request.description,
                systemPrompt = request.systemPrompt,
                responseTemplate = request.responseTemplate,
                toolNames = request.toolNames,
                requiresApproval = request.requiresApproval
            )
            val persisted = skillRepository.save(skill)
            embedSkill(persisted)
            skillCacheService.invalidate()
            persisted
        }!!

        log.info { "Created skill: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Partially updates the skill with the given ID, applying only non-null fields.
     *
     * Re-embeds the skill description if it was changed. Both the DB update and
     * embedding update run inside the transaction so that an embedding failure
     * rolls back the DB change.
     */
    fun update(id: UUID, request: UpdateSkillRequest): Skill {
        log.debug { "Processing update request for skill: id=$id" }

        val saved = transactionTemplate.execute {
            // Step 0: Check if the skill is protected
            val existing = skillRepository.findById(id).orElseThrow {
                log.warn { "Attempt to update non-existent skill: id=$id" }
                NotFoundException("Skill not found")
            }
            if (existing.isProtected) {
                throw BadRequestException("Protected skills cannot be modified")
            }

            // Step 1: Execute partial update
            val persisted = skillRepository.patchUpdate(id, request)
                ?: run {
                    log.warn { "Attempt to update non-existent skill: id=$id" }
                    throw NotFoundException("Skill not found")
                }

            // Step 2: Update embedding if name or description changed
            if (request.name != null || request.description != null) {
                removeEmbeddingsBySkillId(persisted.id.toString())
                embedSkill(persisted)
            }

            skillCacheService.invalidate()
            persisted
        }!!

        log.info { "Updated skill: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Deletes the skill with the given ID and removes its embedding from the store.
     *
     * The embedding is removed inside the transaction so that a removal failure
     * rolls back the DB delete, keeping both stores consistent.
     */
    fun delete(id: UUID) {
        log.debug { "Processing delete request for skill: id=$id" }

        transactionTemplate.execute {
            val existing = skillRepository.findById(id).orElseThrow {
                log.warn { "Attempt to delete non-existent skill: id=$id" }
                NotFoundException("Skill not found")
            }
            if (existing.isProtected) {
                throw BadRequestException("Protected skills cannot be deleted")
            }

            removeEmbeddingsBySkillId(id.toString())
            skillRepository.deleteById(id)
            skillCacheService.invalidate()
        }

        log.info { "Deleted skill: id=$id" }
    }

    /**
     * Embeds the skill's name and description and stores it in the embedding store
     * with the skill ID and a content hash in the metadata.
     */
    private fun embedSkill(skill: Skill) {
        val embeddingText = buildEmbeddingText(skill)
        val embedding = embeddingModel.embed(embeddingText).content()
        val contentHash = sha256(embeddingText)
        val metadata = Metadata.from("skillId", skill.id.toString()).put("contentHash", contentHash).put("type", "skill")
        val segment = TextSegment.from(embeddingText, metadata)
        embeddingStore.add(embedding, segment)
    }

    /**
     * Combines the skill's name and description into a single string for embedding.
     */
    private fun buildEmbeddingText(skill: Skill): String {
        return "Skill: ${skill.name}. ${skill.description}"
    }

    /**
     * Removes all embeddings associated with the given skill ID from the embedding store.
     */
    private fun removeEmbeddingsBySkillId(skillId: String) {
        embeddingStore.removeAll(metadataKey("skillId").isEqualTo(skillId))
    }

    /**
     * Computes a SHA-256 hex digest of the given [input] string,
     * used as a content hash for embedding deduplication.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
