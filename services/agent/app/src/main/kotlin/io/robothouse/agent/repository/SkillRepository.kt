package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for [Skill] entities with support for partial updates
 * via the [UpdateSkillRepository] custom fragment and filtered queries
 * via the [FilterSkillRepository] custom fragment.
 */
interface SkillRepository : JpaRepository<Skill, UUID>, UpdateSkillRepository, FilterSkillRepository {

    /**
     * Finds a skill by its exact [name], or `null` if no match exists.
     */
    fun findByName(name: String): Skill?
}
