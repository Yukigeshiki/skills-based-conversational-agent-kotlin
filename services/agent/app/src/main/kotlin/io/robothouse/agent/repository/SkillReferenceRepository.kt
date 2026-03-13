package io.robothouse.agent.repository

import io.robothouse.agent.entity.SkillReference
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for [SkillReference] entities with support for partial updates
 * via the [UpdateSkillReferenceRepository] custom fragment.
 */
interface SkillReferenceRepository : JpaRepository<SkillReference, UUID>, UpdateSkillReferenceRepository {

    /**
     * Returns all references belonging to the skill identified by [skillId].
     */
    fun findBySkillId(skillId: UUID): List<SkillReference>

    /**
     * Returns true if at least one reference exists for the given [skillId].
     */
    fun existsBySkillId(skillId: UUID): Boolean

    /**
     * Finds a reference by its owning [skillId] and exact [name], or `null` if no match exists.
     */
    fun findBySkillIdAndName(skillId: UUID, name: String): SkillReference?
}
