package io.robothouse.agent.repository

import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.model.UpdateSkillReferenceRequest
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementation of [UpdateSkillReferenceRepository] that builds dynamic JPQL
 * update queries from non-null request fields using parameterized queries.
 */
@Repository
class UpdateSkillReferenceRepositoryImpl(
    private val entityManager: EntityManager
) : UpdateSkillReferenceRepository {

    override fun patchUpdate(id: UUID, request: UpdateSkillReferenceRequest): SkillReference? {
        val setClauses = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        request.name?.let {
            setClauses.add("r.name = :name")
            params["name"] = it
        }
        request.content?.let {
            setClauses.add("r.content = :content")
            params["content"] = it
        }

        if (setClauses.isEmpty()) {
            return entityManager.find(SkillReference::class.java, id)
        }

        val jpql = "UPDATE SkillReference r SET ${setClauses.joinToString(", ")} WHERE r.id = :id"
        val query = entityManager.createQuery(jpql)
        query.setParameter("id", id)
        params.forEach { (key, value) -> query.setParameter(key, value) }

        val updatedCount = query.executeUpdate()
        if (updatedCount == 0) return null

        entityManager.clear()
        return entityManager.find(SkillReference::class.java, id)
    }
}
