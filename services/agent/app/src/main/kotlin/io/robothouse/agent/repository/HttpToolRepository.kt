package io.robothouse.agent.repository

import io.robothouse.agent.entity.HttpTool
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for [HttpTool] entities with support for partial
 * updates via the [UpdateHttpToolRepository] custom fragment.
 */
interface HttpToolRepository : JpaRepository<HttpTool, UUID>, UpdateHttpToolRepository {

    /**
     * Returns the http tool with the given name, or `null` if no match exists.
     */
    fun findByName(name: String): HttpTool?
}
