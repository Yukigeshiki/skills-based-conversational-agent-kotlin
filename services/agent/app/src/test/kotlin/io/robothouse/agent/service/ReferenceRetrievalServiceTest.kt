package io.robothouse.agent.service

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.EmbeddingMatch
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingSearchResult
import dev.langchain4j.store.embedding.EmbeddingStore
import io.robothouse.agent.config.ReferenceProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.entity.SkillReference
import io.robothouse.agent.repository.SkillReferenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ReferenceRetrievalServiceTest {

    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()
    private val referenceProperties = ReferenceProperties(chunkTargetTokens = 500, chunkOverlapTokens = 50, retrievalMaxResults = 5, retrievalMinScore = 0.5)
    private val skillReferenceRepository: SkillReferenceRepository = mock()

    private val service = ReferenceRetrievalService(embeddingModel, embeddingStore, referenceProperties, skillReferenceRepository)

    private val embedding = Embedding.from(FloatArray(1536) { 0.1f })

    @Test
    fun `returns empty list without embedding when skill has no references`() {
        val skillId = UUID.randomUUID()
        whenever(skillReferenceRepository.existsBySkillId(skillId)).thenReturn(false)

        val result = service.retrieveChunks(skillId, "test query")

        assertTrue(result.isEmpty())
        verify(embeddingModel, never()).embed(any<String>())
        verify(embeddingStore, never()).search(any<EmbeddingSearchRequest>())
    }

    @Test
    fun `returns empty list when no matches found`() {
        val skillId = UUID.randomUUID()
        whenever(skillReferenceRepository.existsBySkillId(skillId)).thenReturn(true)
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))

        val result = service.retrieveChunks(skillId, "test query")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns retrieved chunks with correct metadata`() {
        val skillId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val skill = Skill(id = skillId, name = "test-skill", description = "test", systemPrompt = "prompt")
        val reference = SkillReference(id = referenceId, skill = skill, name = "api-docs", content = "content")

        whenever(skillReferenceRepository.existsBySkillId(skillId)).thenReturn(true)
        whenever(skillReferenceRepository.findAllById(listOf(referenceId))).thenReturn(listOf(reference))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val metadata = Metadata.from("skillId", skillId.toString())
            .put("referenceId", referenceId.toString())
            .put("chunkIndex", 2)
            .put("type", "reference")
        val segment = TextSegment.from("This is the chunk content", metadata)
        val match = EmbeddingMatch(0.85, "1", embedding, segment)

        whenever(embeddingStore.search(any<EmbeddingSearchRequest>()))
            .thenReturn(EmbeddingSearchResult(listOf(match)))

        val result = service.retrieveChunks(skillId, "tell me about the API")

        assertEquals(1, result.size)
        assertEquals("api-docs", result[0].referenceName)
        assertEquals(referenceId.toString(), result[0].referenceId)
        assertEquals(2, result[0].chunkIndex)
        assertEquals("This is the chunk content", result[0].content)
        assertEquals(0.85, result[0].score)
    }

    @Test
    fun `returns multiple chunks sorted by relevance`() {
        val skillId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val skill = Skill(id = skillId, name = "test-skill", description = "test", systemPrompt = "prompt")
        val reference = SkillReference(id = referenceId, skill = skill, name = "docs", content = "content")

        whenever(skillReferenceRepository.existsBySkillId(skillId)).thenReturn(true)
        whenever(skillReferenceRepository.findAllById(listOf(referenceId))).thenReturn(listOf(reference))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val metadata1 = Metadata.from("skillId", skillId.toString())
            .put("referenceId", referenceId.toString())
            .put("chunkIndex", 0)
            .put("type", "reference")
        val metadata2 = Metadata.from("skillId", skillId.toString())
            .put("referenceId", referenceId.toString())
            .put("chunkIndex", 1)
            .put("type", "reference")

        val match1 = EmbeddingMatch(0.9, "1", embedding, TextSegment.from("chunk 1", metadata1))
        val match2 = EmbeddingMatch(0.7, "2", embedding, TextSegment.from("chunk 2", metadata2))

        whenever(embeddingStore.search(any<EmbeddingSearchRequest>()))
            .thenReturn(EmbeddingSearchResult(listOf(match1, match2)))

        val result = service.retrieveChunks(skillId, "query")

        assertEquals(2, result.size)
        assertEquals("chunk 1", result[0].content)
        assertEquals("chunk 2", result[1].content)
    }
}
