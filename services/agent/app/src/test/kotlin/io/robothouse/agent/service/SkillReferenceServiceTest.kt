package io.robothouse.agent.service

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.Filter
import io.robothouse.agent.config.ReferenceProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.exception.BadRequestException
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.CreateSkillReferenceRequest
import io.robothouse.agent.model.UpdateSkillReferenceRequest
import io.robothouse.agent.repository.SkillReferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.UUID

class SkillReferenceServiceTest {

    private val skillReferenceRepository: SkillReferenceRepository = mock()
    private val skillService: SkillService = mock()
    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()
    private val chunkingService: ChunkingService = mock()
    private val transactionTemplate: TransactionTemplate = mock()
    private val referenceProperties = ReferenceProperties(chunkTargetTokens = 500, chunkOverlapTokens = 50, retrievalMaxResults = 5, retrievalMinScore = 0.5)

    private lateinit var service: SkillReferenceService

    private val skillId = UUID.randomUUID()
    private val referenceId = UUID.randomUUID()
    private val skill = Skill(id = skillId, name = "test-skill", description = "test", systemPrompt = "prompt")
    private val reference = SkillReference(id = referenceId, skill = skill, name = "test-ref", content = "Some content")

    @BeforeEach
    fun setUp() {
        service = SkillReferenceService(
            skillReferenceRepository, skillService, embeddingModel,
            embeddingStore, chunkingService, transactionTemplate, referenceProperties
        )

        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<org.springframework.transaction.support.TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
    }

    @Test
    fun `create saves reference and embeds chunks`() {
        val request = CreateSkillReferenceRequest(name = "docs", content = "Reference content here.")
        val embedding = Embedding(FloatArray(384) { 0.1f })

        whenever(skillService.findById(skillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findBySkillIdAndName(skillId, "docs")).thenReturn(null)
        whenever(skillReferenceRepository.save(any<SkillReference>())).thenReturn(reference)
        whenever(chunkingService.chunk(any(), any(), any())).thenReturn(listOf("chunk1", "chunk2"))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val result = service.create(skillId, request)

        assertNotNull(result)
        verify(skillReferenceRepository).save(any<SkillReference>())
        verify(embeddingStore, times(2)).add(any(), argThat<TextSegment> { segment ->
            segment.metadata().getString("type") == "reference" &&
                segment.metadata().getString("skillId") == skillId.toString()
        })
    }

    @Test
    fun `create throws when skill not found`() {
        val request = CreateSkillReferenceRequest(name = "docs", content = "content")
        whenever(skillService.findById(skillId)).thenThrow(NotFoundException("Skill not found"))

        assertThrows<NotFoundException> {
            service.create(skillId, request)
        }
    }

    @Test
    fun `create throws when name already exists for skill`() {
        val request = CreateSkillReferenceRequest(name = "test-ref", content = "content")

        whenever(skillService.findById(skillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findBySkillIdAndName(skillId, "test-ref")).thenReturn(reference)

        assertThrows<BadRequestException> {
            service.create(skillId, request)
        }
    }

    @Test
    fun `update re-embeds when content changes`() {
        val request = UpdateSkillReferenceRequest(content = "Updated content")
        val embedding = Embedding(FloatArray(384) { 0.2f })

        whenever(skillService.findById(skillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findById(referenceId)).thenReturn(Optional.of(reference))
        whenever(skillReferenceRepository.patchUpdate(any(), any())).thenReturn(reference)
        whenever(chunkingService.chunk(any(), any(), any())).thenReturn(listOf("updated chunk"))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        service.update(skillId, referenceId, request)

        verify(embeddingStore).removeAll(any<Filter>())
        verify(embeddingStore).add(any(), any<TextSegment>())
    }

    @Test
    fun `delete removes embeddings and entity`() {
        whenever(skillService.findById(skillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findById(referenceId)).thenReturn(Optional.of(reference))

        service.delete(skillId, referenceId)

        verify(embeddingStore).removeAll(any<Filter>())
        verify(skillReferenceRepository).deleteById(referenceId)
    }

    @Test
    fun `delete throws when reference not found`() {
        whenever(skillService.findById(skillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findById(referenceId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.delete(skillId, referenceId)
        }

        verify(embeddingStore, never()).removeAll(any<Filter>())
    }

    @Test
    fun `findById validates skill ownership`() {
        val otherSkillId = UUID.randomUUID()
        whenever(skillService.findById(otherSkillId)).thenReturn(skill)
        whenever(skillReferenceRepository.findById(referenceId)).thenReturn(Optional.of(reference))

        assertThrows<NotFoundException> {
            service.findById(otherSkillId, referenceId)
        }
    }
}
