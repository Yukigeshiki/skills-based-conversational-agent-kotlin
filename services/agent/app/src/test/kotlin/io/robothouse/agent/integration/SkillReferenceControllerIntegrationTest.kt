package io.robothouse.agent.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.repository.SkillReferenceRepository
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
class SkillReferenceControllerIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var skillRepository: SkillRepository

    @Autowired
    lateinit var skillReferenceRepository: SkillReferenceRepository

    private val objectMapper = jacksonObjectMapper()

    private lateinit var testSkill: Skill

    @BeforeEach
    fun setUp() {
        skillReferenceRepository.deleteAll()
        skillRepository.deleteAll()
        testSkill = skillRepository.save(
            Skill(name = "ref-test-skill", description = "Test", systemPrompt = "Prompt", toolNames = emptyList())
        )
    }

    @Test
    fun `POST creates reference and returns 201`() {
        val request = mapOf(
            "name" to "api-docs",
            "content" to "This is the API documentation content."
        )

        mockMvc.post("/api/skills/${testSkill.id}/references") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("api-docs") }
            jsonPath("$.content") { value("This is the API documentation content.") }
            jsonPath("$.id") { exists() }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @Test
    fun `POST returns 400 for blank name`() {
        val request = mapOf(
            "name" to "",
            "content" to "Some content"
        )

        mockMvc.post("/api/skills/${testSkill.id}/references") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST returns 400 for duplicate name`() {
        skillReferenceRepository.save(
            SkillReference(skill = testSkill, name = "existing-ref", content = "content")
        )

        val request = mapOf(
            "name" to "existing-ref",
            "content" to "Different content"
        )

        mockMvc.post("/api/skills/${testSkill.id}/references") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST returns 404 for non-existent skill`() {
        val request = mapOf(
            "name" to "orphan-ref",
            "content" to "content"
        )

        mockMvc.post("/api/skills/00000000-0000-0000-0000-000000000000/references") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET returns list of references`() {
        skillReferenceRepository.save(SkillReference(skill = testSkill, name = "ref-a", content = "A"))
        skillReferenceRepository.save(SkillReference(skill = testSkill, name = "ref-b", content = "B"))

        mockMvc.get("/api/skills/${testSkill.id}/references")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { exists() }
                jsonPath("$[1].name") { exists() }
            }
    }

    @Test
    fun `GET by ID returns reference`() {
        val saved = skillReferenceRepository.save(
            SkillReference(skill = testSkill, name = "get-ref", content = "Content here")
        )

        mockMvc.get("/api/skills/${testSkill.id}/references/${saved.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("get-ref") }
                jsonPath("$.content") { value("Content here") }
            }
    }

    @Test
    fun `GET by ID returns 404 for non-existent reference`() {
        mockMvc.get("/api/skills/${testSkill.id}/references/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `PATCH updates reference`() {
        val saved = skillReferenceRepository.save(
            SkillReference(skill = testSkill, name = "patch-ref", content = "Original content")
        )

        val update = mapOf("content" to "Updated content")

        mockMvc.patch("/api/skills/${testSkill.id}/references/${saved.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(update)
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { value("Updated content") }
            jsonPath("$.name") { value("patch-ref") }
        }
    }

    @Test
    fun `DELETE removes reference and returns 204`() {
        val saved = skillReferenceRepository.save(
            SkillReference(skill = testSkill, name = "delete-ref", content = "Content")
        )

        mockMvc.delete("/api/skills/${testSkill.id}/references/${saved.id}")
            .andExpect {
                status { isNoContent() }
            }

        mockMvc.get("/api/skills/${testSkill.id}/references/${saved.id}")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `DELETE returns 404 for non-existent reference`() {
        mockMvc.delete("/api/skills/${testSkill.id}/references/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
            }
    }
}
