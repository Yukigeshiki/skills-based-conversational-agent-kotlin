package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import jakarta.persistence.Column
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Implementation of [FilterSkillRepository] that builds dynamic native SQL
 * to filter skills by search term and/or tool names.
 *
 * Tool names are stored as a JSON array in the `tool_names` column
 * (e.g. `["DateTimeTool","WebSearchTool"]`), so each tool filter uses
 * a LIKE clause matching within the JSON string.
 */
@Repository
class FilterSkillRepositoryImpl(
    private val entityManager: EntityManager
) : FilterSkillRepository {

    companion object {
        /**
         * Maps Kotlin property names to their database column names,
         * derived from JPA `@Column` annotations on the [Skill] entity.
         * Falls back to the property name if no explicit column name is set.
         */
        private val COLUMN_MAP: Map<String, String> = Skill::class.memberProperties.associate { prop ->
            val columnAnnotation = prop.javaField?.getAnnotation(Column::class.java)
            val dbColumn = columnAnnotation?.name?.takeIf { it.isNotEmpty() } ?: prop.name
            prop.name to dbColumn
        }
    }

    override fun findAllFilteredPaged(search: String?, tools: List<String>?, pageable: Pageable): Page<Skill> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        buildFilterConditions(search, tools, conditions, params)

        val whereClause = if (conditions.isNotEmpty()) "WHERE ${conditions.joinToString(" AND ")}" else ""
        val orderByClause = buildOrderByClause(pageable)

        // Data query
        val dataSql = "SELECT s.* FROM skills s $whereClause $orderByClause LIMIT :limit OFFSET :offset"
        val dataQuery = entityManager.createNativeQuery(dataSql, Skill::class.java)
        params.forEach { (key, value) -> dataQuery.setParameter(key, value) }
        dataQuery.setParameter("limit", pageable.pageSize)
        dataQuery.setParameter("offset", pageable.offset)

        @Suppress("UNCHECKED_CAST")
        val content = dataQuery.resultList as List<Skill>

        // Count query (lazy via PageableExecutionUtils)
        val countSql = "SELECT COUNT(*) FROM skills s $whereClause"
        return PageableExecutionUtils.getPage(content, pageable) {
            val countQuery = entityManager.createNativeQuery(countSql)
            params.forEach { (key, value) -> countQuery.setParameter(key, value) }
            (countQuery.singleResult as Number).toLong()
        }
    }

    private fun buildFilterConditions(
        search: String?,
        tools: List<String>?,
        conditions: MutableList<String>,
        params: MutableMap<String, Any>
    ) {
        search?.let {
            conditions.add("(LOWER(s.name) LIKE LOWER(:search) ESCAPE '\\' OR LOWER(s.description) LIKE LOWER(:search) ESCAPE '\\')")
            val escaped = it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            params["search"] = "%$escaped%"
        }

        val nonEmptyTools = tools?.filter { it.isNotBlank() }
        if (!nonEmptyTools.isNullOrEmpty()) {
            val toolConditions = nonEmptyTools.mapIndexed { index, tool ->
                val paramName = "tool$index"
                // Escape LIKE wildcards and ensure the tool name is matched exactly within the JSON array.
                // Tool names are expected to be alphanumeric identifiers (no quotes or special chars).
                val escapedTool = tool.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                params[paramName] = "%\"$escapedTool\"%"
                "s.tool_names LIKE :$paramName ESCAPE '\\'"
            }
            conditions.add("(${toolConditions.joinToString(" OR ")})")
        }
    }

    private fun buildOrderByClause(pageable: Pageable): String {
        val sortOrders = pageable.sort.toList()
        if (sortOrders.isEmpty()) return "ORDER BY s.created_at DESC"

        val orderClauses = sortOrders.map { order ->
            val column = COLUMN_MAP[order.property]
                ?: throw IllegalArgumentException("Unmapped sort property: ${order.property}")
            val direction = if (order.isAscending) "ASC" else "DESC"
            "s.$column $direction"
        }
        return "ORDER BY ${orderClauses.joinToString(", ")}"
    }
}
