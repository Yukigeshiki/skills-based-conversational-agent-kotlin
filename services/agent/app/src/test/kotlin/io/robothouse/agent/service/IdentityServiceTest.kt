package io.robothouse.agent.service

import io.robothouse.agent.entity.Identity
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.UpdateIdentityRequest
import io.robothouse.agent.repository.IdentityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional

class IdentityServiceTest {

    private val identityRepository: IdentityRepository = mock()
    private val transactionTemplate: TransactionTemplate = mock()
    private lateinit var service: IdentityService

    private val identity = Identity(
        id = 1,
        systemPrompt = "You are a friendly pirate."
    )

    @BeforeEach
    fun setUp() {
        service = IdentityService(identityRepository, transactionTemplate)
        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<org.springframework.transaction.support.TransactionCallback<Any>>(0)
            callback.doInTransaction(mock())
        }
    }

    @Test
    fun `get returns identity configuration`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.of(identity))
        val result = service.get()
        assertEquals("You are a friendly pirate.", result.systemPrompt)
    }

    @Test
    fun `get throws NotFoundException when row is missing`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> { service.get() }
    }

    @Test
    fun `update persists new system prompt`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.of(identity))
        whenever(identityRepository.save(any<Identity>())).thenAnswer { it.getArgument<Identity>(0) }

        val result = service.update(UpdateIdentityRequest(systemPrompt = "You are a helpful robot."))

        assertEquals("You are a helpful robot.", result.systemPrompt)
    }

    @Test
    fun `update throws NotFoundException when row is missing`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.empty())
        assertThrows<NotFoundException> {
            service.update(UpdateIdentityRequest(systemPrompt = "test test test test test test test test test test test"))
        }
    }

    @Test
    fun `getSystemPrompt returns cached prompt after loadCache`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.of(identity))
        service.loadCache()
        assertEquals("You are a friendly pirate.", service.getSystemPrompt())
    }

    @Test
    fun `getSystemPrompt returns empty string when cache not loaded`() {
        assertEquals("", service.getSystemPrompt())
    }

    @Test
    fun `update refreshes cached system prompt`() {
        whenever(identityRepository.findById(1)).thenReturn(Optional.of(identity))
        whenever(identityRepository.save(any<Identity>())).thenAnswer { it.getArgument<Identity>(0) }

        service.loadCache()
        assertEquals("You are a friendly pirate.", service.getSystemPrompt())

        service.update(UpdateIdentityRequest(systemPrompt = "You are a helpful robot."))
        assertEquals("You are a helpful robot.", service.getSystemPrompt())
    }
}
