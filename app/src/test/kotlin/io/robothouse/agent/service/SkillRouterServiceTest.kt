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
import io.robothouse.agent.config.EmbeddingConfig
import io.robothouse.agent.config.SkillRoutingProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class SkillRouterServiceTest {

    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()
    private val skillRepository: SkillRepository = mock()
    private val properties = SkillRoutingProperties(minSimilarityScore = 0.5, fallbackSkillName = "general-assistant")

    private val routerService = SkillRouterService(embeddingModel, embeddingStore, skillRepository, properties)

    @Test
    fun `routes to matched skill when above threshold`() {
        val skillId = UUID.randomUUID()
        val skill = Skill(id = skillId, name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val embedding = Embedding.from(FloatArray(EmbeddingConfig.EMBEDDING_DIMENSION) { 0.1f })

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val segment = TextSegment.from("time help", Metadata.from("skillId", skillId.toString()))
        val match = EmbeddingMatch(0.9, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(skillId)).thenReturn(Optional.of(skill))

        val result = routerService.route("What time is it?")

        assertEquals("datetime-assistant", result.name)
    }

    @Test
    fun `falls back when no matches`() {
        val embedding = Embedding.from(FloatArray(EmbeddingConfig.EMBEDDING_DIMENSION) { 0.1f })
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("Tell me a joke")

        assertEquals("general-assistant", result.name)
    }

    @Test
    fun `throws when fallback skill not found`() {
        val embedding = Embedding.from(FloatArray(EmbeddingConfig.EMBEDDING_DIMENSION) { 0.1f })

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(null)

        assertThrows<IllegalStateException> {
            routerService.route("Tell me a joke")
        }
    }
}
