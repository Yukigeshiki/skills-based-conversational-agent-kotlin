package io.robothouse.agent.service

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.SkillRequest
import io.robothouse.agent.model.UpdateSkillRequest
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.langchain4j.store.embedding.filter.Filter
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.UUID

class SkillServiceTest {

    private val skillRepository: SkillRepository = mock()
    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()
    private val transactionTemplate: TransactionTemplate = mock()

    private lateinit var service: SkillService

    private val skillId = UUID.randomUUID()
    private val skill = Skill(
        id = skillId,
        name = "test-skill",
        description = "A test skill",
        systemPrompt = "You are a test assistant.",
        toolNames = listOf("TestTool")
    )

    @BeforeEach
    fun setUp() {
        service = SkillService(skillRepository, embeddingModel, embeddingStore, transactionTemplate)

        // Make TransactionTemplate execute the callback directly
        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<org.springframework.transaction.support.TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
    }

    @Test
    fun `findAllPaged delegates to repository without filters`() {
        val pageable = PageRequest.of(0, 10)
        val page: Page<Skill> = PageImpl(listOf(skill))
        whenever(skillRepository.findAll(pageable)).thenReturn(page)

        val result = service.findAllPaged(pageable = pageable)

        assertEquals(1, result.totalElements)
        verify(skillRepository).findAll(pageable)
        verify(skillRepository, never()).findAllFilteredPaged(any(), any(), any())
    }

    @Test
    fun `findAllPaged delegates to filtered query with search`() {
        val pageable = PageRequest.of(0, 10)
        val page: Page<Skill> = PageImpl(listOf(skill))
        whenever(skillRepository.findAllFilteredPaged(eq("test"), eq(null), eq(pageable))).thenReturn(page)

        val result = service.findAllPaged(search = "test", pageable = pageable)

        assertEquals(1, result.totalElements)
        verify(skillRepository).findAllFilteredPaged(eq("test"), eq(null), eq(pageable))
    }

    @Test
    fun `findAllPaged delegates to filtered query with tools`() {
        val pageable = PageRequest.of(0, 10)
        val tools = listOf("TestTool")
        val page: Page<Skill> = PageImpl(listOf(skill))
        whenever(skillRepository.findAllFilteredPaged(eq(null), eq(tools), eq(pageable))).thenReturn(page)

        val result = service.findAllPaged(tools = tools, pageable = pageable)

        assertEquals(1, result.totalElements)
        verify(skillRepository).findAllFilteredPaged(eq(null), eq(tools), eq(pageable))
    }

    @Test
    fun `findAllPaged uses unfiltered query when tools list is empty`() {
        val pageable = PageRequest.of(0, 10)
        val page: Page<Skill> = PageImpl(listOf(skill))
        whenever(skillRepository.findAll(pageable)).thenReturn(page)

        val result = service.findAllPaged(tools = emptyList(), pageable = pageable)

        assertEquals(1, result.totalElements)
        verify(skillRepository).findAll(pageable)
    }

    @Test
    fun `findById returns skill when found`() {
        whenever(skillRepository.findById(skillId)).thenReturn(Optional.of(skill))

        val result = service.findById(skillId)

        assertEquals(skill, result)
    }

    @Test
    fun `findById throws NotFoundException when not found`() {
        whenever(skillRepository.findById(skillId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.findById(skillId)
        }
    }

    @Test
    fun `create saves skill and embeds description`() {
        val request = SkillRequest(
            name = "new-skill",
            description = "A new skill",
            systemPrompt = "System prompt",
            toolNames = listOf("TestTool")
        )
        val embedding = Embedding(FloatArray(384) { 0.1f })

        whenever(skillRepository.findByName("new-skill")).thenReturn(null)
        whenever(skillRepository.save(any<Skill>())).thenReturn(skill)
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val result = service.create(request)

        assertNotNull(result)
        verify(skillRepository).save(any<Skill>())
        verify(embeddingModel).embed(eq("Skill: test-skill. A test skill"))
        verify(embeddingStore).add(eq(embedding), any<TextSegment>())
    }

    @Test
    fun `create throws BadRequestException when name already exists`() {
        val request = SkillRequest(
            name = "test-skill",
            description = "A new skill",
            systemPrompt = "System prompt"
        )

        whenever(skillRepository.findByName("test-skill")).thenReturn(skill)

        assertThrows<BadRequestException> {
            service.create(request)
        }

        verify(skillRepository, never()).save(any<Skill>())
        verify(embeddingStore, never()).add(any(), any<TextSegment>())
    }

    @Test
    fun `update re-embeds when description changes`() {
        val request = UpdateSkillRequest(description = "Updated description")
        val updatedSkill = Skill(
            id = skillId,
            name = "test-skill",
            description = "Updated description",
            systemPrompt = "You are a test assistant.",
            toolNames = listOf("TestTool")
        )
        val embedding = Embedding(FloatArray(384) { 0.2f })

        whenever(skillRepository.patchUpdate(skillId, request)).thenReturn(updatedSkill)
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val result = service.update(skillId, request)

        assertEquals("Updated description", result.description)
        verify(embeddingStore).removeAll(any<Filter>())
        verify(embeddingModel).embed(eq("Skill: test-skill. Updated description"))
        verify(embeddingStore).add(eq(embedding), any<TextSegment>())
    }

    @Test
    fun `update re-embeds when name changes`() {
        val request = UpdateSkillRequest(name = "renamed-skill")
        val renamedSkill = Skill(
            id = skillId,
            name = "renamed-skill",
            description = "A test skill",
            systemPrompt = "You are a test assistant.",
            toolNames = listOf("TestTool")
        )
        val embedding = Embedding(FloatArray(384) { 0.2f })

        whenever(skillRepository.patchUpdate(skillId, request)).thenReturn(renamedSkill)
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val result = service.update(skillId, request)

        assertEquals("renamed-skill", result.name)
        verify(embeddingStore).removeAll(any<Filter>())
        verify(embeddingModel).embed(eq("Skill: renamed-skill. A test skill"))
        verify(embeddingStore).add(eq(embedding), any<TextSegment>())
    }

    @Test
    fun `update does not re-embed when name and description are unchanged`() {
        val request = UpdateSkillRequest(systemPrompt = "Updated prompt")

        whenever(skillRepository.patchUpdate(skillId, request)).thenReturn(skill)

        service.update(skillId, request)

        verify(embeddingStore, never()).removeAll(any<Filter>())
        verify(embeddingModel, never()).embed(any<String>())
        verify(embeddingStore, never()).add(any<Embedding>(), any<TextSegment>())
    }

    @Test
    fun `update throws NotFoundException when skill not found`() {
        val request = UpdateSkillRequest(name = "renamed-skill")

        whenever(skillRepository.patchUpdate(skillId, request)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.update(skillId, request)
        }
    }

    @Test
    fun `delete removes embedding and deletes skill`() {
        whenever(skillRepository.existsById(skillId)).thenReturn(true)

        service.delete(skillId)

        verify(embeddingStore).removeAll(any<Filter>())
        verify(skillRepository).deleteById(skillId)
    }

    @Test
    fun `delete throws NotFoundException when skill not found`() {
        whenever(skillRepository.existsById(skillId)).thenReturn(false)

        assertThrows<NotFoundException> {
            service.delete(skillId)
        }

        verify(embeddingStore, never()).removeAll(any<Filter>())
        verify(skillRepository, never()).deleteById(any<UUID>())
    }

    @Test
    fun `create embeds with correct metadata`() {
        val request = SkillRequest(
            name = "new-skill",
            description = "A new skill",
            systemPrompt = "System prompt"
        )
        val embedding = Embedding(FloatArray(384) { 0.1f })

        whenever(skillRepository.findByName("new-skill")).thenReturn(null)
        whenever(skillRepository.save(any<Skill>())).thenReturn(skill)
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        service.create(request)

        verify(embeddingStore).add(eq(embedding), argThat<TextSegment> { segment ->
            segment.metadata().getString("skillId") == skillId.toString() &&
                segment.metadata().getString("contentHash") != null
        })
    }
}
