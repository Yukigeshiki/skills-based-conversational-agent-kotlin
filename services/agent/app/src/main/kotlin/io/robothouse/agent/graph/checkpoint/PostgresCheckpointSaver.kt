package io.robothouse.agent.graph.checkpoint

import io.robothouse.agent.util.log
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver
import org.bsc.langgraph4j.checkpoint.Checkpoint
import org.bsc.langgraph4j.checkpoint.MemorySaver
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer
import org.bsc.langgraph4j.state.AgentState
import org.springframework.jdbc.core.JdbcTemplate
import java.util.LinkedList

/**
 * Persists graph checkpoints to PostgreSQL as JSONB, extending [MemorySaver]
 * to maintain the in-memory linked list that LangGraph4j depends on while
 * adding durable storage for recovery and audit trails.
 *
 * Uses a [PlainTextStateSerializer] to convert checkpoint state to/from
 * JSON strings stored in the `graph_checkpoints` table's JSONB column.
 */
class PostgresCheckpointSaver(
    private val jdbcTemplate: JdbcTemplate,
    private val stateSerializer: PlainTextStateSerializer<out AgentState>
) : MemorySaver() {

    companion object {
        private const val SELECT_BY_THREAD = "SELECT checkpoint_id, node_id, next_node_id, state::text FROM graph_checkpoints WHERE thread_id = ? ORDER BY id ASC"
        private const val INSERT = "INSERT INTO graph_checkpoints (thread_id, checkpoint_id, node_id, next_node_id, state) VALUES (?, ?, ?, ?, ?::jsonb)"
        private const val UPDATE = "UPDATE graph_checkpoints SET node_id = ?, next_node_id = ?, state = ?::jsonb WHERE thread_id = ? AND checkpoint_id = ?"
        private const val DELETE_BY_THREAD = "DELETE FROM graph_checkpoints WHERE thread_id = ?"
    }

    /**
     * Loads checkpoints from PostgreSQL when a thread's checkpoint list is
     * first accessed. Populates the in-memory list from the database.
     */
    override fun loadedCheckpoints(
        config: RunnableConfig,
        checkpoints: LinkedList<Checkpoint>
    ): LinkedList<Checkpoint> {
        val threadId = config.threadId().orElse(THREAD_ID_DEFAULT)

        try {
            val rows = jdbcTemplate.queryForList(SELECT_BY_THREAD, threadId)

            for (row in rows) {
                val checkpointId = row["checkpoint_id"] as String
                val nodeId = row["node_id"] as? String
                val nextNodeId = row["next_node_id"] as? String
                val stateJson = row["state"] as String

                val stateData = stateSerializer.readDataFromString(stateJson)
                val checkpoint = Checkpoint.builder()
                    .id(checkpointId)
                    .state(stateData)
                    .nodeId(nodeId ?: "")
                    .nextNodeId(nextNodeId ?: "")
                    .build()

                checkpoints.add(checkpoint)
            }
        } catch (e: Exception) {
            log.warn { "Failed to load checkpoints from PostgreSQL for thread $threadId: ${e.message}" }
        }

        return checkpoints
    }

    /**
     * Persists a newly created checkpoint to PostgreSQL.
     */
    override fun insertedCheckpoint(
        config: RunnableConfig,
        checkpoints: LinkedList<Checkpoint>,
        checkpoint: Checkpoint
    ) {
        val threadId = config.threadId().orElse(THREAD_ID_DEFAULT)

        try {
            val stateJson = stateSerializer.writeDataAsString(checkpoint.state)
            jdbcTemplate.update(
                INSERT,
                threadId,
                checkpoint.id,
                checkpoint.nodeId,
                checkpoint.nextNodeId,
                stateJson
            )
        } catch (e: Exception) {
            log.error { "Failed to insert checkpoint to PostgreSQL for thread $threadId: ${e.message}" }
        }
    }

    /**
     * Updates an existing checkpoint in PostgreSQL when its state changes.
     */
    override fun updatedCheckpoint(
        config: RunnableConfig,
        checkpoints: LinkedList<Checkpoint>,
        checkpoint: Checkpoint
    ) {
        val threadId = config.threadId().orElse(THREAD_ID_DEFAULT)

        try {
            val stateJson = stateSerializer.writeDataAsString(checkpoint.state)
            jdbcTemplate.update(
                UPDATE,
                checkpoint.nodeId,
                checkpoint.nextNodeId,
                stateJson,
                threadId,
                checkpoint.id
            )
        } catch (e: Exception) {
            log.error { "Failed to update checkpoint in PostgreSQL for thread $threadId: ${e.message}" }
        }
    }

    /**
     * Removes all checkpoints for a thread from PostgreSQL when released.
     */
    override fun releasedCheckpoints(
        config: RunnableConfig,
        checkpoints: LinkedList<Checkpoint>,
        tag: BaseCheckpointSaver.Tag
    ) {
        val threadId = config.threadId().orElse(THREAD_ID_DEFAULT)

        try {
            jdbcTemplate.update(DELETE_BY_THREAD, threadId)
        } catch (e: Exception) {
            log.warn { "Failed to release checkpoints from PostgreSQL for thread $threadId: ${e.message}" }
        }
    }
}
