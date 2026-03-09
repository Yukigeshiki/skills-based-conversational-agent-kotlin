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
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class SkillRouterServiceTest {

    private val embeddingModel: EmbeddingModel = mock()
    private val embeddingStore: EmbeddingStore<TextSegment> = mock()
    private val skillRepository: SkillRepository = mock()
    private val properties = SkillRoutingProperties(contextRetryThreshold = 0.6)

    private val routerService = SkillRouterService(embeddingModel, embeddingStore, skillRepository, properties)

    private val embedding = Embedding.from(FloatArray(EmbeddingConfig.EMBEDDING_DIMENSION) { 0.1f })

    @Test
    fun `routes to skill when user mentions skill name`() {
        val skill = Skill(id = UUID.randomUUID(), name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillRepository.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("Can you use the datetime-assistant to tell me the time?")

        assertEquals("datetime-assistant", result.name)
        // Should not call embedding model at all
        verify(embeddingModel, times(0)).embed(any<String>())
    }

    @Test
    fun `routes to skill by name case-insensitively`() {
        val skill = Skill(id = UUID.randomUUID(), name = "DateTime-Assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillRepository.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("Please use datetime-assistant for this")

        assertEquals("DateTime-Assistant", result.name)
    }

    @Test
    fun `does not match fallback skill by name`() {
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillRepository.findAll()).thenReturn(listOf(fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("use the general-assistant skill")

        assertEquals("general-assistant", result.name)
        // Should fall through to embedding search since fallback is excluded from name matching
        verify(embeddingModel).embed(any<String>())
    }

    @Test
    fun `routes to highest scoring skill`() {
        val skillId = UUID.randomUUID()
        val skill = Skill(id = skillId, name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("time help", Metadata.from("skillId", skillId.toString()))
        val match = EmbeddingMatch(0.9, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(skillId)).thenReturn(Optional.of(skill))

        val result = routerService.route("What time is it?")

        assertEquals("datetime-assistant", result.name)
    }

    @Test
    fun `returns fallback when it scores above threshold`() {
        val fallbackId = UUID.randomUUID()
        val fallbackSkill = Skill(id = fallbackId, name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("general", Metadata.from("skillId", fallbackId.toString()))
        val match = EmbeddingMatch(0.75, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(fallbackId)).thenReturn(Optional.of(fallbackSkill))

        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM.")
        )

        val result = routerService.route("thanks", history)

        assertEquals("general-assistant", result.name)
        // No context retry — score is above threshold
        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `falls back when no embedding matches exist`() {
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("Tell me a joke")

        assertEquals("general-assistant", result.name)
    }

    @Test
    fun `throws when fallback skill not found`() {
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(null)

        assertThrows<io.robothouse.agent.exception.NotFoundException> {
            routerService.route("Tell me a joke")
        }
    }

    @Test
    fun `retries with context when fallback scores below threshold`() {
        val fallbackId = UUID.randomUUID()
        val fallbackSkill = Skill(id = fallbackId, name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())
        val specialistId = UUID.randomUUID()
        val specialistSkill = Skill(id = specialistId, name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM in Tokyo.")
        )

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))

        val fallbackSegment = TextSegment.from("general", Metadata.from("skillId", fallbackId.toString()))
        val fallbackMatch = EmbeddingMatch(0.4, "1", embedding, fallbackSegment)
        val specialistSegment = TextSegment.from("time help", Metadata.from("skillId", specialistId.toString()))
        val specialistMatch = EmbeddingMatch(0.8, "2", embedding, specialistSegment)

        whenever(embeddingStore.search(any<EmbeddingSearchRequest>()))
            .thenReturn(EmbeddingSearchResult(listOf(fallbackMatch)))
            .thenReturn(EmbeddingSearchResult(listOf(specialistMatch)))
        whenever(skillRepository.findById(fallbackId)).thenReturn(Optional.of(fallbackSkill))
        whenever(skillRepository.findById(specialistId)).thenReturn(Optional.of(specialistSkill))

        val result = routerService.route("yes", history)

        assertEquals("datetime-assistant", result.name)
        verify(embeddingModel).embed("yes")
        verify(embeddingModel).embed(argThat<String> { contains("Previous assistant response: It is 3:00 PM in Tokyo.") && endsWith("\nCurrent message: yes") })
    }

    @Test
    fun `skips context retry when direct match is a specialist skill`() {
        val skillId = UUID.randomUUID()
        val skill = Skill(id = skillId, name = "horticulturalist", description = "gardening", systemPrompt = "prompt", toolNames = emptyList())
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM in Tokyo.")
        )

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("gardening", Metadata.from("skillId", skillId.toString()))
        val match = EmbeddingMatch(0.9, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(skillId)).thenReturn(Optional.of(skill))

        val result = routerService.route("Tell me about growing potatoes", history)

        assertEquals("horticulturalist", result.name)
        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `does not retry with context when history is empty`() {
        val fallbackId = UUID.randomUUID()
        val fallbackSkill = Skill(id = fallbackId, name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("general", Metadata.from("skillId", fallbackId.toString()))
        val match = EmbeddingMatch(0.3, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(fallbackId)).thenReturn(Optional.of(fallbackSkill))

        routerService.route("yes", emptyList())

        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `context retry uses last assistant message only`() {
        val fallbackId = UUID.randomUUID()
        val fallbackSkill = Skill(id = fallbackId, name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())
        val history = listOf(
            ConversationMessage(role = "user", content = "First question"),
            ConversationMessage(role = "assistant", content = "First response"),
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM in Tokyo.")
        )

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("general", Metadata.from("skillId", fallbackId.toString()))
        val match = EmbeddingMatch(0.3, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(fallbackId)).thenReturn(Optional.of(fallbackSkill))

        routerService.route("yes", history)

        verify(embeddingModel).embed(argThat<String> {
            contains("Previous assistant response: It is 3:00 PM in Tokyo.")
                && !contains("First response")
                && !contains("First question")
                && endsWith("\nCurrent message: yes")
        })
    }
}
