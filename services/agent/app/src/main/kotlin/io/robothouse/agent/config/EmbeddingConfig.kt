package io.robothouse.agent.config

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

/**
 * Configures the embedding model and pgvector-backed embedding store
 * used for skill routing via semantic similarity.
 */
@Configuration
@EnableConfigurationProperties(SkillRoutingProperties::class)
class EmbeddingConfig {

    companion object {
        const val EMBEDDING_DIMENSION = 384
        const val EMBEDDING_TABLE = "skill_embeddings"
        const val DEFAULT_POSTGRES_PORT = 5432
    }

    @Bean
    fun embeddingModel(): EmbeddingModel = AllMiniLmL6V2EmbeddingModel()

    @Bean
    @ConditionalOnProperty(name = ["spring.datasource.driver-class-name"], havingValue = "org.postgresql.Driver")
    fun embeddingStore(dataSourceProperties: DataSourceProperties): PgVectorEmbeddingStore {
        val jdbcUrl = dataSourceProperties.url
            ?: throw IllegalStateException("spring.datasource.url is required for embedding store")
        val uri = URI(jdbcUrl.removePrefix("jdbc:"))

        val host = uri.host
        val port = if (uri.port > 0) uri.port else DEFAULT_POSTGRES_PORT
        val database = uri.path.removePrefix("/")

        return PgVectorEmbeddingStore.builder()
            .host(host)
            .port(port)
            .database(database)
            .user(dataSourceProperties.username)
            .password(dataSourceProperties.password)
            .table(EMBEDDING_TABLE)
            .dimension(EMBEDDING_DIMENSION)
            .createTable(true)
            .build()
    }
}
