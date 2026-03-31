package io.robothouse.agent.repository

import io.robothouse.agent.entity.HttpTool
import io.robothouse.agent.model.UpdateHttpToolRequest
import java.util.UUID

/**
 * Custom repository fragment for partial http tool updates using
 * dynamic JPQL.
 */
interface UpdateHttpToolRepository {

    /**
     * Applies a partial update to the http tool with the given ID.
     *
     * Only non-null fields in the request are included in the update query.
     * Returns the updated tool, or null if no tool exists with the given ID.
     */
    fun patchUpdate(id: UUID, request: UpdateHttpToolRequest): HttpTool?
}
