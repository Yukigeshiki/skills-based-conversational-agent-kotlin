package io.robothouse.agent.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.robothouse.agent.config.ConversationMemoryProperties
import io.robothouse.agent.model.ConversationMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class ConversationMemoryServiceTest {

    private val redisTemplate: StringRedisTemplate = mock()
    private val listOps: ListOperations<String, String> = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val properties = ConversationMemoryProperties(maxMessages = 50, ttlHours = 24)

    init {
        whenever(redisTemplate.opsForList()).thenReturn(listOps)
    }

    private val service = ConversationMemoryService(redisTemplate, objectMapper, properties)

    @Test
    fun `addMessage pushes JSON to Redis trims and sets expiry`() {
        val message = ConversationMessage(role = "user", content = "Hello")

        service.addMessage("conv-1", message)

        verify(listOps).rightPush(eq("conversation:conv-1"), any<String>())
        verify(listOps).trim("conversation:conv-1", -50, -1)
        verify(redisTemplate).expire("conversation:conv-1", Duration.ofHours(24))
    }

    @Test
    fun `getHistory returns deserialized messages`() {
        val msg1 = ConversationMessage(role = "user", content = "Hi")
        val msg2 = ConversationMessage(role = "assistant", content = "Hello!")
        whenever(listOps.range("conversation:conv-1", -50, -1)).thenReturn(
            listOf(objectMapper.writeValueAsString(msg1), objectMapper.writeValueAsString(msg2))
        )

        val history = service.getHistory("conv-1")

        assertEquals(2, history.size)
        assertEquals("user", history[0].role)
        assertEquals("Hi", history[0].content)
        assertEquals("assistant", history[1].role)
        assertEquals("Hello!", history[1].content)
    }

    @Test
    fun `getHistory returns empty list when key does not exist`() {
        whenever(listOps.range("conversation:conv-2", -50, -1)).thenReturn(null)

        val history = service.getHistory("conv-2")

        assertTrue(history.isEmpty())
    }

    @Test
    fun `getHistory skips malformed entries`() {
        val validMsg = ConversationMessage(role = "user", content = "Hi")
        whenever(listOps.range("conversation:conv-3", -50, -1)).thenReturn(
            listOf("not-valid-json", objectMapper.writeValueAsString(validMsg))
        )

        val history = service.getHistory("conv-3")

        assertEquals(1, history.size)
        assertEquals("Hi", history[0].content)
    }

    @Test
    fun `getHistory respects maxMessages limit`() {
        val properties = ConversationMemoryProperties(maxMessages = 2, ttlHours = 24)
        val service = ConversationMemoryService(redisTemplate, objectMapper, properties)

        val messages = (1..5).map { ConversationMessage(role = "user", content = "msg $it") }
        whenever(listOps.range("conversation:conv-4", -2, -1)).thenReturn(
            messages.takeLast(2).map { objectMapper.writeValueAsString(it) }
        )

        val history = service.getHistory("conv-4")

        assertEquals(2, history.size)
        assertEquals("msg 4", history[0].content)
        assertEquals("msg 5", history[1].content)
    }

    @Test
    fun `addMessage propagates Redis exceptions`() {
        whenever(listOps.rightPush(any<String>(), any<String>())).thenThrow(RuntimeException("Redis down"))

        assertThrows<RuntimeException> {
            service.addMessage("conv-5", ConversationMessage(role = "user", content = "Hi"))
        }
    }

    @Test
    fun `getHistory propagates Redis exceptions`() {
        whenever(listOps.range(any<String>(), any(), any())).thenThrow(RuntimeException("Redis down"))

        assertThrows<RuntimeException> {
            service.getHistory("conv-6")
        }
    }
}
