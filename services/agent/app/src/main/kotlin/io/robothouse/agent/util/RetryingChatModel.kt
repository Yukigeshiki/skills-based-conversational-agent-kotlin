package io.robothouse.agent.util

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.config.LlmRetryProperties
import java.util.concurrent.ThreadLocalRandom

/**
 * Decorator for [ChatModel] that retries transient failures with jittered
 * exponential backoff. Retry classification is delegated to [LlmRetryClassifier]
 * so application bugs and non-transient errors fail fast. Before each retry,
 * the next backoff sleep is compared against the agent's remaining wall-clock
 * budget (when one is installed via [LlmRetryEventEmitter]) so the decorator
 * does not sleep into a known deadline overrun. The in-flight call itself is
 * bounded by LangChain4j's HTTP timeout. Any overshoot of the agent budget
 * is caught immediately by `ctx.checkTimeout(...)` in the agent loop after the
 * call returns — so the decorator does not need to reserve the full per-call
 * timeout in advance. Mirrors the structure of [RetryingStreamingChatModel]
 * for consistency.
 */
class RetryingChatModel(
    private val delegate: ChatModel,
    private val properties: LlmRetryProperties
) : ChatModel {

    override fun chat(request: ChatRequest): ChatResponse {
        var attempt = 1
        var delay = properties.initialDelayMs

        while (true) {
            val outcome = runCatching { delegate.chat(request) }
            if (outcome.isSuccess) return outcome.getOrThrow()

            val error = outcome.exceptionOrNull()!!
            if (!LlmRetryClassifier.isRetryable(error)) {
                throw error
            }
            if (attempt >= properties.maxAttempts) {
                log.warn {
                    "LLM retries exhausted after $attempt attempt(s): " +
                        "${error.javaClass.simpleName}: ${error.message}"
                }
                throw error
            }

            val nextSleepMs = applyJitter(delay)
            val remainingBudget = LlmRetryEventEmitter.remainingBudgetMs()
            if (remainingBudget != null && remainingBudget < nextSleepMs) {
                // Even the backoff sleep would push us past the agent's wall-clock
                // budget — give up rather than retrying into a known overrun. The
                // in-flight call itself is bounded by the LangChain4j HTTP timeout,
                // and the agent loop's post-call checkTimeout enforces the strict
                // deadline if the next attempt does overshoot.
                log.warn {
                    "LLM retry budget exhausted before attempt ${attempt + 1} " +
                        "(remaining=${remainingBudget}ms, next sleep=${nextSleepMs}ms): " +
                        "${error.javaClass.simpleName}: ${error.message}"
                }
                throw error
            }

            log.warn {
                "Retrying LLM call (attempt ${attempt + 1}/${properties.maxAttempts}): " +
                    "${error.javaClass.simpleName}: ${error.message}"
            }
            LlmRetryEventEmitter.emit(attempt + 1, properties.maxAttempts, LlmRetryClassifier.safeDescription(error))
            Thread.sleep(nextSleepMs)
            // Re-check after the sleep: the boundary case `remainingBudget == nextSleepMs`
            // slips through the pre-sleep check, and we don't want to issue another
            // delegate.chat(...) once the deadline has actually been reached. Otherwise
            // the next attempt can pin the calling thread for up to the LangChain4j HTTP
            // timeout before checkTimeout fires in the agent loop.
            val budgetAfterSleep = LlmRetryEventEmitter.remainingBudgetMs()
            if (budgetAfterSleep != null && budgetAfterSleep <= 0) {
                log.warn {
                    "LLM retry budget expired during backoff sleep before attempt ${attempt + 1} " +
                        "(remaining=${budgetAfterSleep}ms): " +
                        "${error.javaClass.simpleName}: ${error.message}"
                }
                throw error
            }
            delay = (delay.toDouble() * properties.delayFactor).toLong()
                .coerceAtMost(properties.maxDelayMs)
            attempt++
        }
    }

    /** Applies symmetric proportional jitter, clamped to non-negative. No-op when jitter is disabled. */
    private fun applyJitter(delayMs: Long): Long {
        val jitterRange = (delayMs.toDouble() * properties.jitterFactor).toLong()
        if (jitterRange <= 0) return delayMs
        val offset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1)
        return (delayMs + offset).coerceAtLeast(0)
    }
}
