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
@Import(PostgresContainerConfig::class, RedisContainerConfig::class, TestEmbeddingConfig::class)
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
            "description" to "An integration test skill that handles date and time queries for users",
            "systemPrompt" to "You are a test assistant.",
            "toolNames" to listOf("DateTimeTool")
        )

        mockMvc.post("/api/skills") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("integration-skill") }
            jsonPath("$.description") { value("An integration test skill that handles date and time queries for users") }
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
    fun `GET returns paged skills`() {
        skillRepository.save(
            Skill(name = "skill-a", description = "A", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "skill-b", description = "B", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.totalPages") { value(1) }
                jsonPath("$.first") { value(true) }
                jsonPath("$.last") { value(true) }
            }
    }

    @Test
    fun `GET supports pagination`() {
        repeat(5) { i ->
            skillRepository.save(
                Skill(name = "paged-skill-$i", description = "Skill $i", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
            )
        }

        mockMvc.get("/api/skills?page=0&size=2&sort=name,asc")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
                jsonPath("$.first") { value(true) }
                jsonPath("$.last") { value(false) }
            }

        mockMvc.get("/api/skills?page=2&size=2&sort=name,asc")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.first") { value(false) }
                jsonPath("$.last") { value(true) }
            }
    }

    @Test
    fun `GET filters by search term`() {
        skillRepository.save(
            Skill(name = "greeting-skill", description = "Says hello", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "weather-skill", description = "Gets weather", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills?search=greeting")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].name") { value("greeting-skill") }
            }
    }

    @Test
    fun `GET filters by search term in description`() {
        skillRepository.save(
            Skill(name = "skill-a", description = "Handles weather forecasts", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "skill-b", description = "Sends emails", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills?search=weather")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].name") { value("skill-a") }
            }
    }

    @Test
    fun `GET filters by tool names`() {
        skillRepository.save(
            Skill(name = "datetime-skill", description = "D", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "multi-tool-skill", description = "M", systemPrompt = "P", toolNames = listOf("DateTimeTool", "WebSearchTool"))
        )

        mockMvc.get("/api/skills?tools=WebSearchTool")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].name") { value("multi-tool-skill") }
            }
    }

    @Test
    fun `GET filters by multiple tool names with OR logic`() {
        skillRepository.save(
            Skill(name = "date-skill", description = "D", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "web-skill", description = "W", systemPrompt = "P", toolNames = listOf("WebSearchTool"))
        )
        skillRepository.save(
            Skill(name = "other-skill", description = "O", systemPrompt = "P", toolNames = listOf("OtherTool"))
        )

        mockMvc.get("/api/skills?tools=DateTimeTool&tools=WebSearchTool")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(2) }
                jsonPath("$.totalElements") { value(2) }
            }
    }

    @Test
    fun `GET combines search and tool filters`() {
        skillRepository.save(
            Skill(name = "greeting-datetime", description = "Greets with time", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "greeting-web", description = "Greets from web", systemPrompt = "P", toolNames = listOf("WebSearchTool"))
        )
        skillRepository.save(
            Skill(name = "weather-datetime", description = "Weather with time", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills?search=greeting&tools=DateTimeTool")
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()") { value(1) }
                jsonPath("$.content[0].name") { value("greeting-datetime") }
            }
    }

    @Test
    fun `GET sorts by name ascending`() {
        skillRepository.save(
            Skill(name = "charlie", description = "C", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "alpha", description = "A", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )
        skillRepository.save(
            Skill(name = "bravo", description = "B", systemPrompt = "P", toolNames = listOf("DateTimeTool"))
        )

        mockMvc.get("/api/skills?sort=name,asc")
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].name") { value("alpha") }
                jsonPath("$.content[1].name") { value("bravo") }
                jsonPath("$.content[2].name") { value("charlie") }
            }
    }

    @Test
    fun `GET returns 400 for invalid sort property`() {
        mockMvc.get("/api/skills?sort=nonExistentField,asc")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `GET returns 400 for invalid sort direction`() {
        mockMvc.get("/api/skills?sort=name,invalid")
            .andExpect {
                status { isBadRequest() }
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

        val update = mapOf(
            "name" to "patch-skill",
            "description" to "Updated description that is long enough to satisfy the minimum length requirement",
            "systemPrompt" to "P",
            "toolNames" to listOf("DateTimeTool")
        )

        mockMvc.patch("/api/skills/${saved.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(update)
        }.andExpect {
            status { isOk() }
            jsonPath("$.description") { value("Updated description that is long enough to satisfy the minimum length requirement") }
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
