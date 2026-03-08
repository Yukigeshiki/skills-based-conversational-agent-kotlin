package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Custom repository fragment for filtered skill queries with dynamic tool name OR matching.
 */
interface FilterSkillRepository {

    /**
     * Returns a paged result of skills matching the given filters. The [search] parameter matches
     * against name or description (case-insensitive, partial). The [tools] parameter
     * matches skills that use any of the specified tool names (OR logic).
     */
    fun findAllFilteredPaged(search: String?, tools: List<String>?, pageable: Pageable): Page<Skill>
}
