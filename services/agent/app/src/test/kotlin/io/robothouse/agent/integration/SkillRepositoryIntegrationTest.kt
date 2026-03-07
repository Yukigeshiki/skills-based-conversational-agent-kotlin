package io.robothouse.agent.integration

import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
}
