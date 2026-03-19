package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.UpdateSkillRequest
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementation of [UpdateSkillRepository] that builds dynamic JPQL
 * update queries from non-null request fields using parameterized queries.
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
        request.responseTemplate?.let {
            setClauses.add("s.responseTemplate = :responseTemplate")
            params["responseTemplate"] = it
        }
        request.toolNames?.let {
            setClauses.add("s.toolNames = :toolNames")
            params["toolNames"] = it
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
