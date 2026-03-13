package io.robothouse.agent.service

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import io.robothouse.agent.config.ReferenceProperties
import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.CreateSkillReferenceRequest
import io.robothouse.agent.model.UpdateSkillReferenceRequest
import io.robothouse.agent.repository.SkillReferenceRepository
import io.robothouse.agent.util.log
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Manages skill reference CRUD operations and maintains embedding store consistency.
 *
 * When a reference is created or updated, its content is chunked and each chunk
 * is embedded with metadata linking it back to the skill and reference for
 * filtered retrieval at chat time.
 *
 * Note: Embedding operations run inside the DB transaction but the embedding store
 * is not transactional. If embedding fails partway through (e.g. on the 3rd of 5 chunks),
 * the DB transaction rolls back but already-added embeddings remain orphaned in the store.
 * These orphans reference a non-existent referenceId and will be cleaned up on the next
 * successful create or update for the same reference.
 */
@Service
@EnableConfigurationProperties(ReferenceProperties::class)
class SkillReferenceService(
    private val skillReferenceRepository: SkillReferenceRepository,
    private val skillService: SkillService,
    private val embeddingModel: EmbeddingModel,
    private val embeddingStore: EmbeddingStore<TextSegment>,
    private val chunkingService: ChunkingService,
    private val transactionTemplate: TransactionTemplate,
    private val referenceProperties: ReferenceProperties
) {

    /**
     * Creates a new reference for the given skill, chunks its content, and
     * embeds each chunk in the embedding store.
     */
    fun create(skillId: UUID, request: CreateSkillReferenceRequest): SkillReference {
        log.debug { "Creating reference '${request.name}' for skill: id=$skillId" }

        val saved = transactionTemplate.execute {
            val skill = skillService.findById(skillId)

            if (skillReferenceRepository.findBySkillIdAndName(skillId, request.name) != null) {
                throw BadRequestException("A reference with name '${request.name}' already exists for this skill")
            }

            val reference = SkillReference(
                skill = skill,
                name = request.name,
                content = request.content
            )
            val persisted = skillReferenceRepository.save(reference)
            embedReferenceChunks(persisted)
            persisted
        }!!

        log.info { "Created reference: id=${saved.id}, name=${saved.name}, skillId=$skillId" }
        return saved
    }

    /**
     * Partially updates a reference. If the content changed, removes old embeddings
     * and re-chunks/re-embeds the new content.
     */
    fun update(skillId: UUID, referenceId: UUID, request: UpdateSkillReferenceRequest): SkillReference {
        log.debug { "Updating reference: id=$referenceId, skillId=$skillId" }

        val saved = transactionTemplate.execute {
            val existing = findAndValidateOwnership(skillId, referenceId)

            request.name?.let {
                if (it != existing.name) {
                    val duplicate = skillReferenceRepository.findBySkillIdAndName(skillId, it)
                    if (duplicate != null && duplicate.id != referenceId) {
                        throw BadRequestException("A reference with name '$it' already exists for this skill")
                    }
                }
            }

            val persisted = skillReferenceRepository.patchUpdate(referenceId, request)
                ?: throw NotFoundException("Reference not found")

            request.content?.let {
                removeEmbeddingsByReferenceId(referenceId.toString())
                embedReferenceChunks(persisted)
            }

            persisted
        }!!

        log.info { "Updated reference: id=${saved.id}, name=${saved.name}" }
        return saved
    }

    /**
     * Deletes a reference and removes its embeddings from the store.
     */
    fun delete(skillId: UUID, referenceId: UUID) {
        log.debug { "Deleting reference: id=$referenceId, skillId=$skillId" }

        transactionTemplate.execute {
            findAndValidateOwnership(skillId, referenceId)
            removeEmbeddingsByReferenceId(referenceId.toString())
            skillReferenceRepository.deleteById(referenceId)
        }

        log.info { "Deleted reference: id=$referenceId" }
    }

    /**
     * Returns all references for the given skill.
     */
    fun findBySkillId(skillId: UUID): List<SkillReference> {
        validateSkillExists(skillId)
        return skillReferenceRepository.findBySkillId(skillId)
    }

    /**
     * Returns a single reference by ID, validating skill ownership.
     */
    fun findById(skillId: UUID, referenceId: UUID): SkillReference {
        return findAndValidateOwnership(skillId, referenceId)
    }

    /**
     * Fetches a reference by [referenceId] and verifies that it belongs to the
     * given [skillId]. Throws [NotFoundException] if the skill, reference, or
     * ownership check fails.
     */
    private fun findAndValidateOwnership(skillId: UUID, referenceId: UUID): SkillReference {
        validateSkillExists(skillId)
        val reference = skillReferenceRepository.findById(referenceId).orElseThrow {
            NotFoundException("Reference not found")
        }
        if (reference.skill.id != skillId) {
            throw NotFoundException("Reference not found")
        }
        return reference
    }

    /**
     * Throws [NotFoundException] if no skill exists with the given [skillId].
     */
    private fun validateSkillExists(skillId: UUID) {
        skillService.findById(skillId)
    }

    /**
     * Chunks the reference content and embeds each chunk with metadata for
     * filtered retrieval.
     */
    private fun embedReferenceChunks(reference: SkillReference) {
        val chunks = chunkingService.chunk(
            reference.content,
            referenceProperties.chunkTargetTokens,
            referenceProperties.chunkOverlapTokens
        )

        log.debug { "Embedding ${chunks.size} chunk(s) for reference: id=${reference.id}, name=${reference.name}" }

        chunks.forEachIndexed { index, chunkText ->
            val embedding = embeddingModel.embed(chunkText).content()
            val metadata = Metadata.from("skillId", reference.skill.id.toString())
                .put("referenceId", reference.id.toString())
                .put("chunkIndex", index)
                .put("type", "reference")
            val segment = TextSegment.from(chunkText, metadata)
            embeddingStore.add(embedding, segment)
        }
    }

    /**
     * Removes all embedding store entries associated with the given [referenceId].
     */
    private fun removeEmbeddingsByReferenceId(referenceId: String) {
        embeddingStore.removeAll(metadataKey("referenceId").isEqualTo(referenceId))
    }
}
