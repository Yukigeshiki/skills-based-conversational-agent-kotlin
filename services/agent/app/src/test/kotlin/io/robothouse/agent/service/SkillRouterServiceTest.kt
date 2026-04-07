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
import io.robothouse.agent.config.SkillRoutingProperties
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.repository.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
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
    private val skillCacheService: SkillCacheService = mock()
    private val properties = SkillRoutingProperties(minSimilarityThreshold = 0.60, minTokenOverlapRatio = 0.3)
    private val queryEnrichmentService: QueryEnrichmentService = mock()

    private val routerService = SkillRouterService(
        embeddingModel, embeddingStore, skillRepository, skillCacheService, properties, queryEnrichmentService
    )

    private val embedding = Embedding.from(FloatArray(1536) { 0.1f })

    init {
        // Default: enrichment returns raw query so existing tests work unchanged
        whenever(queryEnrichmentService.enrich(any(), any())).thenAnswer { it.arguments[0] as String }
    }

    @Test
    fun `routes to skill when user mentions skill name`() {
        val skill = Skill(id = UUID.randomUUID(), name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("Can you use the datetime-assistant to tell me the time?")

        assertEquals("datetime-assistant", result.name)
        verify(embeddingModel, times(0)).embed(any<String>())
    }

    @Test
    fun `routes to skill by name case-insensitively`() {
        val skill = Skill(id = UUID.randomUUID(), name = "DateTime-Assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("Please use datetime-assistant for this")

        assertEquals("DateTime-Assistant", result.name)
    }

    @Test
    fun `does not match fallback skill by name`() {
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("use the general-assistant skill")

        assertEquals("general-assistant", result.name)
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
    fun `routes to specialist on single embedding pass`() {
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
    fun `falls back when score is below minimum similarity threshold`() {
        val skillId = UUID.randomUUID()
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("time help", Metadata.from("skillId", skillId.toString()))
        val match = EmbeddingMatch(0.5, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("something vague")

        assertEquals("general-assistant", result.name)
    }

    // --- New enrichment tests ---

    @Test
    fun `calls enrichment service with message and history`() {
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM.")
        )

        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        routerService.route("thanks", history)

        verify(queryEnrichmentService).enrich("thanks", history)
    }

    @Test
    fun `uses enriched query for embedding search`() {
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())
        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM.")
        )

        whenever(queryEnrichmentService.enrich(any(), any())).thenReturn("enriched time zone query about Tokyo")
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        routerService.route("yes", history)

        verify(embeddingModel).embed("enriched time zone query about Tokyo")
    }

    @Test
    fun `enriched query routes to specialist that raw query would miss`() {
        val specialistId = UUID.randomUUID()
        val specialistSkill = Skill(id = specialistId, name = "datetime-assistant", description = "time help", systemPrompt = "prompt", toolNames = listOf("DateTimeTool"))

        whenever(queryEnrichmentService.enrich(any(), any())).thenReturn("What is the current time in Tokyo, Japan?")
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        val segment = TextSegment.from("time help", Metadata.from("skillId", specialistId.toString()))
        val match = EmbeddingMatch(0.9, "1", embedding, segment)
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(listOf(match)))
        whenever(skillRepository.findById(specialistId)).thenReturn(Optional.of(specialistSkill))

        val history = listOf(
            ConversationMessage(role = "user", content = "What time is it in Tokyo?"),
            ConversationMessage(role = "assistant", content = "It is 3:00 PM in Tokyo.")
        )

        val result = routerService.route("yes", history)

        assertEquals("datetime-assistant", result.name)
    }

    @Test
    fun `falls back when enriched query still has no match`() {
        val fallbackSkill = Skill(name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(queryEnrichmentService.enrich(any(), any())).thenReturn("enriched but still vague query")
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("do it")

        assertEquals("general-assistant", result.name)
    }

    // --- Token overlap tests ---

    @Test
    fun `routes to skill by token overlap when enough tokens match`() {
        val skill = Skill(id = UUID.randomUUID(), name = "weather-forecast", description = "Helps with weather forecasts and temperature predictions for cities worldwide", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("what is the weather forecast temperature predictions for cities")

        assertEquals("weather-forecast", result.name)
        verify(skillCacheService, atLeastOnce()).findAll()
        verify(embeddingModel, times(0)).embed(any<String>())
        verify(queryEnrichmentService, times(0)).enrich(any(), any())
    }

    @Test
    fun `falls through to embedding when token overlap score is below threshold`() {
        val skill = Skill(id = UUID.randomUUID(), name = "weather-forecast", description = "Helps with weather forecasts", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("is it cold outside")

        assertEquals("general-assistant", result.name)
        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `picks highest scoring skill when multiple skills match token overlap`() {
        val skillA = Skill(id = UUID.randomUUID(), name = "weather-forecast", description = "Helps with weather forecasts and temperature predictions for cities worldwide", systemPrompt = "prompt", toolNames = emptyList())
        val skillB = Skill(id = UUID.randomUUID(), name = "travel-planner", description = "Plans travel itineraries for cities and weather conditions", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skillA, skillB, fallbackSkill))

        val result = routerService.route("weather forecast temperature predictions for cities")

        assertEquals("weather-forecast", result.name)
    }

    @Test
    fun `excludes fallback skill from token overlap matching`() {
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general purpose assistant that helps with everything and anything you need", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("general purpose assistant that helps with everything")

        assertEquals("general-assistant", result.name)
        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `handles message with only short tokens gracefully`() {
        val skill = Skill(id = UUID.randomUUID(), name = "weather-forecast", description = "weather help", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        val result = routerService.route("I a")

        assertEquals("general-assistant", result.name)
    }

    @Test
    fun `token overlap matches when tokens overlap exactly`() {
        val skill = Skill(id = UUID.randomUUID(), name = "datetime-assistant", description = "Handles timezone conversions and scheduling", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))

        val result = routerService.route("what datetime timezone scheduling")

        assertEquals("datetime-assistant", result.name)
        verify(embeddingModel, times(0)).embed(any<String>())
    }

    @Test
    fun `token overlap filters stop words and short tokens`() {
        val skill = Skill(id = UUID.randomUUID(), name = "weather-forecast", description = "Provides forecasts", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skill, fallbackSkill))
        whenever(embeddingModel.embed(any<String>())).thenReturn(Response(embedding))
        whenever(embeddingStore.search(any<EmbeddingSearchRequest>())).thenReturn(EmbeddingSearchResult(emptyList()))
        whenever(skillRepository.findByName("general-assistant")).thenReturn(fallbackSkill)

        // After stop word + length filter, only "tomorrow" remains as a meaningful token,
        // which does not appear in the skill tokens — so token overlap should not match.
        val result = routerService.route("what is the for an by tomorrow")

        assertEquals("general-assistant", result.name)
        verify(embeddingModel, times(1)).embed(any<String>())
    }

    @Test
    fun `token overlap tie-break is deterministic by skill name ascending`() {
        // Two skills with identical descriptions will produce the same Jaccard score.
        // The tie-break sorts by skill name ascending, so "alpha-skill" wins over "beta-skill".
        val skillAlpha = Skill(id = UUID.randomUUID(), name = "alpha-skill", description = "weather forecast temperature predictions cities", systemPrompt = "prompt", toolNames = emptyList())
        val skillBeta = Skill(id = UUID.randomUUID(), name = "beta-skill", description = "weather forecast temperature predictions cities", systemPrompt = "prompt", toolNames = emptyList())
        val fallbackSkill = Skill(id = UUID.randomUUID(), name = "general-assistant", description = "general", systemPrompt = "prompt", toolNames = emptyList())

        whenever(skillCacheService.findAll()).thenReturn(listOf(skillBeta, skillAlpha, fallbackSkill))

        val result = routerService.route("weather forecast temperature predictions cities")

        assertEquals("alpha-skill", result.name)
    }
}
