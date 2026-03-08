package io.robothouse.agent.integration

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@Import(PostgresContainerConfig::class, RedisContainerConfig::class)
class SkillRepositoryIntegrationTest {

    @Autowired
    lateinit var skillRepository: SkillRepository

    @BeforeEach
    fun setUp() {
        skillRepository.deleteAll()
    }

    @Test
    fun `save and retrieve skill with PostgreSQL`() {
        val skill = Skill(
            name = "test-skill-${System.nanoTime()}",
            description = "A test skill",
            systemPrompt = "You are a test assistant.",
            toolNames = listOf("DateTimeTool")
        )

        val saved = skillRepository.save(skill)

        assertNotNull(saved.id)
        assertNotNull(saved.createdAt)
        assertNotNull(saved.updatedAt)

        val found = skillRepository.findById(saved.id!!).orElse(null)
        assertNotNull(found)
        assertEquals(saved.name, found.name)
        assertEquals(saved.description, found.description)
        assertEquals(listOf("DateTimeTool"), found.toolNames)
    }

    @Test
    fun `findByName returns skill`() {
        val name = "findbyname-skill-${System.nanoTime()}"
        skillRepository.save(
            Skill(
                name = name,
                description = "Test",
                systemPrompt = "Prompt",
                toolNames = listOf("DateTimeTool")
            )
        )

        val found = skillRepository.findByName(name)
        assertNotNull(found)
        assertEquals(name, found?.name)
    }

    @Test
    fun `findByName returns null for non-existent skill`() {
        assertNull(skillRepository.findByName("non-existent-skill"))
    }

    @Test
    @Transactional
    fun `patchUpdate updates only provided fields`() {
        val skill = skillRepository.save(
            Skill(
                name = "patch-test-${System.nanoTime()}",
                description = "Original description",
                systemPrompt = "Original prompt",
                toolNames = listOf("DateTimeTool")
            )
        )

        val updated = skillRepository.patchUpdate(
            skill.id!!,
            UpdateSkillRequest(description = "Updated description")
        )

        assertNotNull(updated)
        assertEquals("Updated description", updated!!.description)
        assertEquals("Original prompt", updated.systemPrompt)
        assertEquals(listOf("DateTimeTool"), updated.toolNames)
    }

    @Test
    fun `delete removes skill`() {
        val skill = skillRepository.save(
            Skill(
                name = "delete-test-${System.nanoTime()}",
                description = "To be deleted",
                systemPrompt = "Prompt",
                toolNames = listOf("DateTimeTool")
            )
        )

        skillRepository.deleteById(skill.id!!)

        assertTrue(skillRepository.findById(skill.id!!).isEmpty)
    }

    @Test
    fun `tool names persisted as JSON and retrieved correctly`() {
        val toolNames = listOf("DateTimeTool", "AnotherTool")
        val skill = skillRepository.save(
            Skill(
                name = "json-tools-${System.nanoTime()}",
                description = "Multi-tool skill",
                systemPrompt = "Prompt",
                toolNames = toolNames
            )
        )

        val found = skillRepository.findById(skill.id!!).get()
        assertEquals(toolNames, found.toolNames)
    }

    @Test
    fun `findAllFilteredPaged filters by search term in name`() {
        skillRepository.save(Skill(name = "greeting-skill", description = "Says hello", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "weather-skill", description = "Gets weather", systemPrompt = "P", toolNames = listOf("DateTimeTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged("greeting", null, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("greeting-skill", result.content[0].name)
    }

    @Test
    fun `findAllFilteredPaged filters by search term in description`() {
        skillRepository.save(Skill(name = "skill-a", description = "Handles weather forecasts", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "skill-b", description = "Sends emails", systemPrompt = "P", toolNames = listOf("DateTimeTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged("weather", null, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("skill-a", result.content[0].name)
    }

    @Test
    fun `findAllFilteredPaged search is case insensitive`() {
        skillRepository.save(Skill(name = "Greeting-Skill", description = "Says Hello", systemPrompt = "P", toolNames = listOf("DateTimeTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged("greeting", null, pageable)

        assertEquals(1, result.totalElements)
    }

    @Test
    fun `findAllFilteredPaged filters by tool names`() {
        skillRepository.save(Skill(name = "date-skill", description = "D", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "web-skill", description = "W", systemPrompt = "P", toolNames = listOf("WebSearchTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged(null, listOf("WebSearchTool"), pageable)

        assertEquals(1, result.totalElements)
        assertEquals("web-skill", result.content[0].name)
    }

    @Test
    fun `findAllFilteredPaged filters by multiple tools with OR logic`() {
        skillRepository.save(Skill(name = "date-skill", description = "D", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "web-skill", description = "W", systemPrompt = "P", toolNames = listOf("WebSearchTool")))
        skillRepository.save(Skill(name = "other-skill", description = "O", systemPrompt = "P", toolNames = listOf("OtherTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged(null, listOf("DateTimeTool", "WebSearchTool"), pageable)

        assertEquals(2, result.totalElements)
    }

    @Test
    fun `findAllFilteredPaged combines search and tool filters`() {
        skillRepository.save(Skill(name = "greeting-datetime", description = "Greets with time", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "greeting-web", description = "Greets from web", systemPrompt = "P", toolNames = listOf("WebSearchTool")))
        skillRepository.save(Skill(name = "weather-datetime", description = "Weather with time", systemPrompt = "P", toolNames = listOf("DateTimeTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged("greeting", listOf("DateTimeTool"), pageable)

        assertEquals(1, result.totalElements)
        assertEquals("greeting-datetime", result.content[0].name)
    }

    @Test
    fun `findAllFilteredPaged paginates results correctly`() {
        repeat(5) { i ->
            skillRepository.save(Skill(name = "paged-skill-$i", description = "Skill $i", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        }

        val pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged(null, null, pageable)

        assertEquals(2, result.content.size)
        assertEquals(5, result.totalElements)
        assertEquals(3, result.totalPages)
        assertTrue(result.isFirst)
    }

    @Test
    fun `findAllFilteredPaged escapes LIKE wildcards in search`() {
        skillRepository.save(Skill(name = "100% complete", description = "Done", systemPrompt = "P", toolNames = listOf("DateTimeTool")))
        skillRepository.save(Skill(name = "another-skill", description = "Not done", systemPrompt = "P", toolNames = listOf("DateTimeTool")))

        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"))
        val result = skillRepository.findAllFilteredPaged("100%", null, pageable)

        assertEquals(1, result.totalElements)
        assertEquals("100% complete", result.content[0].name)
    }
}
