package io.robothouse.agent.integration

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Test configuration that overrides the OpenAI embedding model with a local
 * AllMiniLmL6V2 model so integration tests remain offline, fast, and free.
 */
@TestConfiguration
class TestEmbeddingConfig {

    companion object {
        const val TEST_EMBEDDING_DIMENSION = 384
    }

    @Bean
    @Primary
    fun testEmbeddingModel(): EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    @Bean
    @Primary
    fun testEmbeddingStore(dataSource: DataSource): PgVectorEmbeddingStore =
        PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)
            .table("skill_embeddings")
            .dimension(TEST_EMBEDDING_DIMENSION)
            .createTable(true)
            .build()
}
