package io.robothouse.agent.repository

import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.model.HttpToolParameter
import io.robothouse.agent.model.UpdateHttpToolRequest
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementation of [UpdateHttpToolRepository] that applies partial updates
 * to a managed [HttpTool] entity, letting the persistence context flush
 * changes and trigger JPA lifecycle callbacks (`@PreUpdate`).
 *
 * Clearable fields (maps, lists) use three-way semantics:
 * - `null` in the request means the field was not sent (skip)
 * - empty means the field should be cleared
 * - non-empty means the field should be set to the value
 */
@Repository
class UpdateHttpToolRepositoryImpl(
    private val entityManager: EntityManager
) : UpdateHttpToolRepository {

    override fun patchUpdate(id: UUID, request: UpdateHttpToolRequest): HttpTool? {
        val tool = entityManager.find(HttpTool::class.java, id) ?: return null

        request.name?.let { tool.name = it }
        request.description?.let { tool.description = it }
        request.endpointUrl?.let { tool.endpointUrl = it }
        request.httpMethod?.let { tool.httpMethod = it }

        if (request.headers != null) {
            tool.headers = request.headers
        }

        if (request.parameters != null) {
            tool.parameters = request.parameters.map { p ->
                HttpToolParameter(name = p.name, type = p.type, description = p.description, required = p.required)
            }
        }

        request.timeoutSeconds?.let { tool.timeoutSeconds = it }
        request.maxResponseLength?.let { tool.maxResponseLength = it }

        entityManager.flush()
        return tool
    }
}
