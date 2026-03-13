package io.robothouse.agent.repository

import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.model.UpdateSkillReferenceRequest
import java.util.UUID

/**
 * Custom repository fragment for partial skill reference updates using dynamic JPQL.
 */
interface UpdateSkillReferenceRepository {

    /**
     * Applies a partial update to the skill reference with the given ID.
     *
     * Only non-null fields in the request are included in the update query.
     * Returns the updated reference, or null if no reference exists with the given ID.
     */
    fun patchUpdate(id: UUID, request: UpdateSkillReferenceRequest): SkillReference?
}
