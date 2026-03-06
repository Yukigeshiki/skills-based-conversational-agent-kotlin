package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.UpdateSkillRequest
import java.util.UUID

/**
 * Custom repository fragment for partial skill updates using dynamic JPQL.
 */
interface UpdateSkillRepository {

    /**
     * Applies a partial update to the skill with the given ID.
     *
     * Only non-null fields in the request are included in the update query.
     * Returns the updated skill, or null if no skill exists with the given ID.
     */
    fun patchUpdate(id: UUID, request: UpdateSkillRequest): Skill?
}
