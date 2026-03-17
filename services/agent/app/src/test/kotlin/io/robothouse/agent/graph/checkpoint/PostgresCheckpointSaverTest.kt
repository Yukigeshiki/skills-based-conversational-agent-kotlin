package io.robothouse.agent.graph.checkpoint

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.bsc.langgraph4j.RunnableConfig
import org.bsc.langgraph4j.checkpoint.Checkpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@JdbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresCheckpointSaverTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_agent_db")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS graph_checkpoints (
                id BIGSERIAL PRIMARY KEY,
                thread_id VARCHAR(255) NOT NULL,
                checkpoint_id VARCHAR(255) NOT NULL,
                node_id VARCHAR(255),
                next_node_id VARCHAR(255),
                state JSONB NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                UNIQUE (thread_id, checkpoint_id)
            )
        """.trimIndent())
    }

    private fun createSaver(): PostgresCheckpointSaver {
        val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(ChatMessageModule())
        return PostgresCheckpointSaver(jdbcTemplate, AgentGraphStateSerializer(objectMapper))
    }

    private fun uniqueThreadId() = "test-${UUID.randomUUID()}"

    @Test
    fun `put stores checkpoint and get retrieves it`() {
        val saver = createSaver()
        val threadId = uniqueThreadId()
        val config = RunnableConfig.builder().threadId(threadId).build()
        val state = mapOf<String, Any>("iteration" to 1, "done" to false, "response" to "hello")
        val checkpoint = Checkpoint.builder()
            .id("cp-1")
            .state(state)
            .nodeId("call_llm")
            .nextNodeId("execute_tools")
            .build()

        saver.put(config, checkpoint)

        val retrieved = saver.get(config)
        assertTrue(retrieved.isPresent)
        assertEquals("cp-1", retrieved.get().id)
        assertEquals("call_llm", retrieved.get().nodeId)
        assertEquals("execute_tools", retrieved.get().nextNodeId)
    }

    @Test
    fun `put persists checkpoint to PostgreSQL as JSONB`() {
        val saver = createSaver()
        val threadId = uniqueThreadId()
        val config = RunnableConfig.builder().threadId(threadId).build()
        val state = mapOf<String, Any>("iteration" to 3, "done" to true, "response" to "done")
        val checkpoint = Checkpoint.builder()
            .id("cp-2")
            .state(state)
            .nodeId("call_llm")
            .nextNodeId("")
            .build()

        saver.put(config, checkpoint)

        val rows = jdbcTemplate.queryForList(
            "SELECT thread_id, checkpoint_id, node_id, state::text FROM graph_checkpoints WHERE thread_id = ?",
            threadId
        )
        assertEquals(1, rows.size)
        assertEquals("cp-2", rows[0]["checkpoint_id"])
        assertEquals("call_llm", rows[0]["node_id"])
        val stateJson = rows[0]["state"] as String
        assertTrue(stateJson.contains("\"iteration\""))
        assertTrue(stateJson.contains("\"done\""))
    }

    @Test
    fun `list returns checkpoints for a thread after multiple puts`() {
        val saver = createSaver()
        val threadId = uniqueThreadId()
        val config = RunnableConfig.builder().threadId(threadId).build()

        saver.put(config, Checkpoint.builder()
            .id("cp-a").state(mapOf("iteration" to 1)).nodeId("n1").nextNodeId("n2").build())
        saver.put(config, Checkpoint.builder()
            .id("cp-b").state(mapOf("iteration" to 2)).nodeId("n2").nextNodeId("n3").build())

        val checkpoints = saver.list(config)
        assertTrue(checkpoints.size >= 2, "Expected at least 2 checkpoints, got ${checkpoints.size}")

        val dbRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM graph_checkpoints WHERE thread_id = ?",
            Int::class.java,
            threadId
        )
        assertTrue(dbRows!! >= 2, "Expected at least 2 rows in DB, got $dbRows")
    }

    @Test
    fun `release removes all checkpoints for a thread`() {
        val saver = createSaver()
        val threadId = uniqueThreadId()
        val config = RunnableConfig.builder().threadId(threadId).build()
        saver.put(config, Checkpoint.builder()
            .id("cp-x").state(mapOf("iteration" to 1)).nodeId("n1").nextNodeId("n2").build())

        saver.release(config)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM graph_checkpoints WHERE thread_id = ?",
            Int::class.java,
            threadId
        )
        assertEquals(0, count)
    }

    @Test
    fun `checkpoints from different threads are isolated`() {
        val saver = createSaver()
        val threadId1 = uniqueThreadId()
        val threadId2 = uniqueThreadId()
        val config1 = RunnableConfig.builder().threadId(threadId1).build()
        val config2 = RunnableConfig.builder().threadId(threadId2).build()

        saver.put(config1, Checkpoint.builder()
            .id("cp-1").state(mapOf("iteration" to 1)).nodeId("n1").nextNodeId("n2").build())
        saver.put(config2, Checkpoint.builder()
            .id("cp-2").state(mapOf("iteration" to 2)).nodeId("n3").nextNodeId("n4").build())

        val thread1Count = saver.list(config1).size
        val thread2Count = saver.list(config2).size
        assertTrue(thread1Count >= 1, "Thread 1 should have checkpoints")
        assertTrue(thread2Count >= 1, "Thread 2 should have checkpoints")

        saver.release(config1)

        assertEquals(0, saver.list(config1).size)
        assertTrue(saver.list(config2).size >= 1, "Thread 2 should still have checkpoints after releasing thread 1")
    }

    @Test
    fun `JSONB state is queryable in PostgreSQL`() {
        val saver = createSaver()
        val threadId = uniqueThreadId()
        val config = RunnableConfig.builder().threadId(threadId).build()
        saver.put(config, Checkpoint.builder()
            .id("cp-q").state(mapOf("iteration" to 5, "done" to true, "response" to "final answer"))
            .nodeId("call_llm").nextNodeId("").build())

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM graph_checkpoints WHERE thread_id = ? AND state->>'done' = 'true'",
            Int::class.java,
            threadId
        )
        assertEquals(1, count)

        val response = jdbcTemplate.queryForObject(
            "SELECT state->>'response' FROM graph_checkpoints WHERE thread_id = ? AND checkpoint_id = 'cp-q'",
            String::class.java,
            threadId
        )
        assertEquals("final answer", response)
    }
}
