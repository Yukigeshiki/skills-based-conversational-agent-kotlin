package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.UpdateSkillRequest
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementation of [UpdateSkillRepository] that builds dynamic JPQL
 * update queries from non-null request fields using parameterized queries.
 *
 * Clearable fields (nullable strings, lists) use three-way semantics:
 * - `null` in the request means the field was not sent (skip)
 * - blank/empty means the field should be cleared
 * - non-blank/non-empty means the field should be set to the value
 */
@Repository
class UpdateSkillRepositoryImpl(
    private val entityManager: EntityManager
) : UpdateSkillRepository {

    override fun patchUpdate(id: UUID, request: UpdateSkillRequest): Skill? {
        val setClauses = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        request.name?.let {
            setClauses.add("s.name = :name")
            params["name"] = it
        }
        request.description?.let {
            setClauses.add("s.description = :description")
            params["description"] = it
        }
        request.systemPrompt?.let {
            setClauses.add("s.systemPrompt = :systemPrompt")
            params["systemPrompt"] = it
        }

        // responseTemplate is nullable in the entity — blank clears it to null
        if (request.responseTemplate != null) {
            setClauses.add("s.responseTemplate = :responseTemplate")
            params["responseTemplate"] = request.responseTemplate.ifBlank { null }
        }

        // toolNames is a list — empty list clears it
        if (request.toolNames != null) {
            setClauses.add("s.toolNames = :toolNames")
            params["toolNames"] = request.toolNames
        }

        request.requiresApproval?.let {
            setClauses.add("s.requiresApproval = :requiresApproval")
            params["requiresApproval"] = it
        }

        if (setClauses.isEmpty()) {
            return entityManager.find(Skill::class.java, id)
        }

        val jpql = "UPDATE Skill s SET ${setClauses.joinToString(", ")} WHERE s.id = :id"
        val query = entityManager.createQuery(jpql)
        query.setParameter("id", id)
        params.forEach { (key, value) -> query.setParameter(key, value) }

        val updatedCount = query.executeUpdate()
        if (updatedCount == 0) return null

        entityManager.clear()
        return entityManager.find(Skill::class.java, id)
    }
}
