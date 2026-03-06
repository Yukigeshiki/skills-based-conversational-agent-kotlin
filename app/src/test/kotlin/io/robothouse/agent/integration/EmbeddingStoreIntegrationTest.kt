package io.robothouse.agent.integration

import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@Import(PostgresContainerConfig::class)
class EmbeddingStoreIntegrationTest {

    @Autowired
    lateinit var embeddingStore: EmbeddingStore<TextSegment>

    @Autowired
    lateinit var embeddingModel: EmbeddingModel

    @BeforeEach
    fun setUp() {
        embeddingStore.removeAll()
    }

    @Test
    fun `store and search embeddings with pgvector`() {
        val skillId = UUID.randomUUID().toString()
        val description = "Helps with date, time, and timezone conversions"

        val embedding = embeddingModel.embed(description).content()
        val segment = TextSegment.from(description, Metadata.from("skillId", skillId))
        embeddingStore.add(embedding, segment)

        val queryEmbedding = embeddingModel.embed("What time is it?").content()
        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .minScore(0.3)
            .build()

        val results = embeddingStore.search(searchRequest)

        assertTrue(results.matches().isNotEmpty())
        assertEquals(skillId, results.matches().first().embedded().metadata().getString("skillId"))
    }

    @Test
    fun `removeAll by metadata filter removes matching embeddings`() {
        val skillId = UUID.randomUUID().toString()
        val description = "Unique test skill for removal ${System.nanoTime()}"

        val embedding = embeddingModel.embed(description).content()
        val segment = TextSegment.from(description, Metadata.from("skillId", skillId))
        embeddingStore.add(embedding, segment)

        embeddingStore.removeAll(metadataKey("skillId").isEqualTo(skillId))

        val queryEmbedding = embeddingModel.embed(description).content()
        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .minScore(0.9)
            .build()

        val results = embeddingStore.search(searchRequest)
        val matchesForSkill = results.matches().filter {
            it.embedded().metadata().getString("skillId") == skillId
        }
        assertTrue(matchesForSkill.isEmpty())
    }

    @Test
    fun `similarity search returns results above threshold`() {
        val skillId1 = UUID.randomUUID().toString()
        val skillId2 = UUID.randomUUID().toString()

        val desc1 = "Handles weather forecasts and climate data"
        val desc2 = "Manages calendar events and scheduling"

        embeddingStore.add(
            embeddingModel.embed(desc1).content(),
            TextSegment.from(desc1, Metadata.from("skillId", skillId1))
        )
        embeddingStore.add(
            embeddingModel.embed(desc2).content(),
            TextSegment.from(desc2, Metadata.from("skillId", skillId2))
        )

        val queryEmbedding = embeddingModel.embed("What's the weather like?").content()
        val searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(1)
            .minScore(0.3)
            .build()

        val results = embeddingStore.search(searchRequest)
        assertTrue(results.matches().isNotEmpty())
        assertEquals(skillId1, results.matches().first().embedded().metadata().getString("skillId"))
    }
}
