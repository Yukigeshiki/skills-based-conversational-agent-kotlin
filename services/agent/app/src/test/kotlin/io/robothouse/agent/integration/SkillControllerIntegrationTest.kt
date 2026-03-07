package io.robothouse.agent.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@Import(PostgresContainerConfig::class, RedisContainerConfig::class)
class SkillControllerIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var skillRepository: SkillRepository

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        skillRepository.deleteAll()
    }

    @Test
    fun `POST creates skill and returns 201`() {
        val request = mapOf(
            "name" to "integration-skill",
            "description" to "An integration test skill",
            "systemPrompt" to "You are a test assistant.",
            "toolNames" to listOf("DateTimeTool")
        )

        mockMvc.post("/api/skills") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("integration-skill") }
            jsonPath("$.description") { value("An integration test skill") }
            jsonPath("$.id") { exists() }
            jsonPath("$.createdAt") { exists() }
            jsonPath("$.updatedAt") { exists() }
        }
    }

    @Test
    fun `POST returns 400 for invalid request`() {
        val request = mapOf(
            "name" to "",
            "description" to "Test",
            "systemPrompt" to "Prompt",
            "toolNames" to listOf("DateTimeTool")
        )

        mockMvc.post("/api/skills") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST returns 400 for unknown tool names`() {
        val request = mapOf(
            "name" to "bad-tools-skill",
            "description" to "Test",
            "systemPrompt" to "Prompt",
            "toolNames" to listOf("NonExistentTool")
        )

        mockMvc.post("/api/skills") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET returns all skills`() {
        skillRepository.save(
            Skill(name = "skill-a", description = "A", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "skill-b", description = "B", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
    }

    @Test
    fun `GET by ID returns skill`() {
        val saved = skillRepository.save(
            Skill(name = "get-by-id", description = "Test", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills/${saved.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("get-by-id") }
            }
    }

    @Test
    fun `GET by ID returns 404 for non-existent skill`() {
        mockMvc.get("/api/skills/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `PATCH updates skill partially`() {
        val saved = skillRepository.save(
            Skill(name = "patch-skill", description = "Original", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        val update = mapOf("description" to "Updated description")

        mockMvc.patch("/api/skills/${saved.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(update)
        }.andExpect {
            status { isOk() }
            jsonPath("$.description") { value("Updated description") }
            jsonPath("$.name") { value("patch-skill") }
        }
    }

    @Test
    fun `DELETE removes skill and returns 204`() {
        val saved = skillRepository.save(
            Skill(name = "delete-skill", description = "To delete", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.delete("/api/skills/${saved.id}")
            .andExpect {
                status { isNoContent() }
            }

        mockMvc.get("/api/skills/${saved.id}")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE returns 404 for non-existent skill`() {
        mockMvc.delete("/api/skills/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
            }
    }
}
