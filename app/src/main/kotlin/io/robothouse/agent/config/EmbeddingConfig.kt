package io.robothouse.agent.config

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

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
    fun embeddingStore(dataSource: DataSource, @Value("\${spring.datasource.password}") password: String): PgVectorEmbeddingStore {
        val connection = dataSource.connection
        val url = connection.metaData.url
        val user = connection.metaData.userName
        connection.close()

        val host = url.substringAfter("://").substringBefore(":")
        val port = url.substringAfter("$host:").substringBefore("/").toIntOrNull() ?: DEFAULT_POSTGRES_PORT
        val database = url.substringAfterLast("/").substringBefore("?")

        return PgVectorEmbeddingStore.builder()
            .host(host)
            .port(port)
            .database(database)
            .user(user)
            .password(password)
            .table(EMBEDDING_TABLE)
            .dimension(EMBEDDING_DIMENSION)
            .createTable(true)
            .build()
    }
}
