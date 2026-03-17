package io.robothouse.agent.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.robothouse.agent.graph.checkpoint.AgentGraphStateSerializer
import io.robothouse.agent.graph.checkpoint.ChatMessageModule
import io.robothouse.agent.graph.checkpoint.OrchestrationGraphStateSerializer
import io.robothouse.agent.graph.checkpoint.PostgresCheckpointSaver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Configures PostgreSQL-backed graph checkpointing when enabled via the
 * `agent.checkpointing-enabled` property. Creates the checkpoint saver,
 * serializers, and a dedicated ObjectMapper with the ChatMessage module.
 */
@Configuration
@ConditionalOnProperty(name = ["agent.checkpointing-enabled"], havingValue = "true")
class CheckpointConfig {

    /**
     * ObjectMapper configured for graph state serialization with support for
     * Langchain4j's ChatMessage hierarchy and Java time types.
     */
    @Bean
    @Qualifier("checkpointObjectMapper")
    fun checkpointObjectMapper(): ObjectMapper {
        return jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(ChatMessageModule())
    }

    /**
     * Serializer for the agent loop graph state, handling Langchain4j's
     * ChatMessage hierarchy and tool execution steps via Jackson.
     */
    @Bean
    fun agentGraphStateSerializer(checkpointObjectMapper: ObjectMapper): AgentGraphStateSerializer {
        return AgentGraphStateSerializer(checkpointObjectMapper)
    }

    /**
     * Serializer for the orchestration graph state, handling Skill entities,
     * AgentResponse, and ConversationMessage lists via Jackson.
     */
    @Bean
    fun orchestrationGraphStateSerializer(checkpointObjectMapper: ObjectMapper): OrchestrationGraphStateSerializer {
        return OrchestrationGraphStateSerializer(checkpointObjectMapper)
    }

    /**
     * Checkpoint saver for the agent loop graph, using the agent state
     * serializer for JSONB persistence.
     */
    @Bean
    @Qualifier("agentCheckpointSaver")
    fun agentCheckpointSaver(
        jdbcTemplate: JdbcTemplate,
        agentGraphStateSerializer: AgentGraphStateSerializer
    ): PostgresCheckpointSaver {
        return PostgresCheckpointSaver(jdbcTemplate, agentGraphStateSerializer)
    }

    /**
     * Checkpoint saver for the orchestration graph, using the orchestration
     * state serializer for JSONB persistence.
     */
    @Bean
    @Qualifier("orchestrationCheckpointSaver")
    fun orchestrationCheckpointSaver(
        jdbcTemplate: JdbcTemplate,
        orchestrationGraphStateSerializer: OrchestrationGraphStateSerializer
    ): PostgresCheckpointSaver {
        return PostgresCheckpointSaver(jdbcTemplate, orchestrationGraphStateSerializer)
    }
}
