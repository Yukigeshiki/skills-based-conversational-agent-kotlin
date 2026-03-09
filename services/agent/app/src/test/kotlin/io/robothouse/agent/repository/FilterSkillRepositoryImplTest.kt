package io.robothouse.agent.repository

import io.robothouse.agent.entity.Skill
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class FilterSkillRepositoryImplTest {

    private val entityManager: EntityManager = mock()
    private val dataQuery: Query = mock()
    private val countQuery: Query = mock()
    private lateinit var repository: FilterSkillRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = FilterSkillRepositoryImpl(entityManager)
    }

    private fun setupQueries(skills: List<Skill> = emptyList(), count: Long = 0L) {
        whenever(entityManager.createNativeQuery(any<String>(), eq(Skill::class.java))).thenReturn(dataQuery)
        whenever(entityManager.createNativeQuery(argThat<String> { startsWith("SELECT COUNT") })).thenReturn(countQuery)
        whenever(dataQuery.setParameter(any<String>(), any())).thenReturn(dataQuery)
        whenever(dataQuery.resultList).thenReturn(skills)
        whenever(countQuery.setParameter(any<String>(), any())).thenReturn(countQuery)
        whenever(countQuery.singleResult).thenReturn(count)
    }

    @Test
    fun `builds query without filters`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged(null, null, pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(!sql.contains("WHERE")) { "Expected no WHERE clause but got: $sql" }
                assert(sql.contains("ORDER BY s.created_at DESC")) { "Expected default ordering" }
                assert(sql.contains("LIMIT :limit OFFSET :offset"))
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `builds query with search filter`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged("test", null, pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("WHERE")) { "Expected WHERE clause" }
                assert(sql.contains("LOWER(s.name) LIKE LOWER(:search)"))
                assert(sql.contains("LOWER(s.description) LIKE LOWER(:search)"))
                assert(sql.contains("ESCAPE '\\'"))
            },
            eq(Skill::class.java)
        )
        verify(dataQuery).setParameter(eq("search"), eq("%test%"))
    }

    @Test
    fun `escapes LIKE wildcards in search term`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged("test%with_wildcards", null, pageable)

        verify(dataQuery).setParameter(eq("search"), eq("%test\\%with\\_wildcards%"))
    }

    @Test
    fun `escapes backslashes in search term`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged("test\\path", null, pageable)

        verify(dataQuery).setParameter(eq("search"), eq("%test\\\\path%"))
    }

    @Test
    fun `builds query with tool filter`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged(null, listOf("DateTimeTool", "WebSearchTool"), pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("WHERE"))
                assert(sql.contains("s.tool_names LIKE :tool0 ESCAPE '\\'"))
                assert(sql.contains("s.tool_names LIKE :tool1 ESCAPE '\\'"))
                assert(sql.contains(" OR ")) { "Tool conditions should be OR-joined" }
            },
            eq(Skill::class.java)
        )
        verify(dataQuery).setParameter(eq("tool0"), eq("%\"DateTimeTool\"%"))
        verify(dataQuery).setParameter(eq("tool1"), eq("%\"WebSearchTool\"%"))
    }

    @Test
    fun `builds query with both search and tool filters`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged("test", listOf("DateTimeTool"), pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("WHERE"))
                assert(sql.contains("LOWER(s.name) LIKE LOWER(:search)"))
                assert(sql.contains("s.tool_names LIKE :tool0"))
                assert(sql.contains(" AND ")) { "Search and tool conditions should be AND-joined" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `filters out blank tool names`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged(null, listOf("DateTimeTool", "", "  "), pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("s.tool_names LIKE :tool0"))
                assert(!sql.contains(":tool1")) { "Blank tools should be filtered out" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `treats all-blank tools list as no filter`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged(null, listOf("", "  "), pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(!sql.contains("WHERE")) { "All-blank tools should not produce WHERE clause" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `builds ORDER BY from pageable sort`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name")))

        repository.findAllFilteredPaged(null, null, pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("ORDER BY s.name ASC")) { "Expected name ASC ordering but got: $sql" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `maps Kotlin property name to database column name`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("systemPrompt")))

        repository.findAllFilteredPaged(null, null, pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("ORDER BY s.system_prompt DESC")) { "Expected system_prompt column but got: $sql" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `supports multiple sort orders`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name"), Sort.Order.desc("createdAt")))

        repository.findAllFilteredPaged(null, null, pageable)

        verify(entityManager).createNativeQuery(
            check<String> { sql ->
                assert(sql.contains("ORDER BY s.name ASC, s.created_at DESC")) { "Expected multi-column ordering but got: $sql" }
            },
            eq(Skill::class.java)
        )
    }

    @Test
    fun `sets limit and offset from pageable`() {
        setupQueries()
        val pageable = PageRequest.of(2, 5)

        repository.findAllFilteredPaged(null, null, pageable)

        verify(dataQuery).setParameter(eq("limit"), eq(5))
        verify(dataQuery).setParameter(eq("offset"), eq(10L))
    }

    @Test
    fun `escapes LIKE wildcards in tool names`() {
        setupQueries()
        val pageable = PageRequest.of(0, 10)

        repository.findAllFilteredPaged(null, listOf("Tool%Name"), pageable)

        verify(dataQuery).setParameter(eq("tool0"), eq("%\"Tool\\%Name\"%"))
    }
}
