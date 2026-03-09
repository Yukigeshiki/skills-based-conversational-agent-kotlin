package io.robothouse.agent.config

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Configures the embedding model and pgvector-backed embedding store
 * used for skill routing via semantic similarity.
 */
@Configuration
@EnableConfigurationProperties(SkillRoutingProperties::class)
class EmbeddingConfig {

    companion object {
        const val EMBEDDING_DIMENSION = 1536
        const val EMBEDDING_TABLE = "skill_embeddings"
    }

    @Bean
    fun embeddingModel(@Value("\${openai.api-key}") apiKey: String): EmbeddingModel =
        OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("text-embedding-3-small")
            .build()

    @Bean
    @ConditionalOnProperty(name = ["spring.datasource.driver-class-name"], havingValue = "org.postgresql.Driver")
    fun embeddingStore(dataSource: DataSource): PgVectorEmbeddingStore =
        PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)
            .table(EMBEDDING_TABLE)
            .dimension(EMBEDDING_DIMENSION)
            .createTable(true)
            .build()
}
