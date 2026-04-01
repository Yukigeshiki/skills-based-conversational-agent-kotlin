package io.robothouse.agent.service

import io.robothouse.agent.entity.Identity
import io.robothouse.agent.exception.NotFoundException
import io.robothouse.agent.model.UpdateIdentityRequest
import io.robothouse.agent.repository.IdentityRepository
import io.robothouse.agent.util.log
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Service layer for reading and updating the singleton identity configuration
 * that provides a global personality prepended to every skill's system prompt.
 *
 * The system prompt is cached in memory on startup and invalidated on update
 * to avoid a database round-trip on every chat request.
 */
@Service
class IdentityService(
    private val identityRepository: IdentityRepository,
    private val transactionTemplate: TransactionTemplate
) {

    companion object {
        private const val SINGLETON_ID = 1
    }

    @Volatile
    private var cachedSystemPrompt: String = ""

    /** Loads the identity system prompt into the in-memory cache on startup. */
    @PostConstruct
    fun loadCache() {
        cachedSystemPrompt = identityRepository.findById(SINGLETON_ID)
            .map { it.systemPrompt }
            .orElse("") ?: ""
        log.info { "Identity system prompt cache loaded (${cachedSystemPrompt.length} chars)" }
    }

    /** Retrieves the singleton identity configuration. */
    fun get(): Identity {
        log.debug { "Retrieving identity configuration" }
        return identityRepository.findById(SINGLETON_ID).orElseThrow {
            log.warn { "Identity configuration row not found" }
            NotFoundException("Identity configuration not found")
        }
    }

    /** Updates the identity system prompt and returns the updated entity. */
    fun update(request: UpdateIdentityRequest): Identity {
        log.debug { "Processing update request for identity configuration" }

        val saved = transactionTemplate.execute {
            val identity = identityRepository.findById(SINGLETON_ID).orElseThrow {
                log.warn { "Identity configuration row not found during update" }
                NotFoundException("Identity configuration not found")
            }

            identity.systemPrompt = request.systemPrompt
            identityRepository.save(identity)
        }!!

        cachedSystemPrompt = saved.systemPrompt
        log.info { "Updated identity configuration and refreshed cache" }
        return saved
    }

    /** Returns the cached identity system prompt. */
    fun getSystemPrompt(): String = cachedSystemPrompt
}
