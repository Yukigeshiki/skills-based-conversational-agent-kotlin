package io.robothouse.agent.util

import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.robothouse.agent.config.LlmRetryProperties
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Decorator for [StreamingChatModel] that retries transient failures with jittered
 * exponential backoff via a manual loop, using [LlmRetryClassifier] for classification.
 * Retries only fire before the first chunk is emitted — once the client is rendering,
 * a retry would duplicate output. The next backoff sleep is checked against the agent's
 * remaining wall-clock budget (when installed via [LlmRetryEventEmitter]) so we don't
 * sleep into a known overrun, and the per-attempt latch wait is capped by the smaller
 * of `attemptTimeoutMs` and the remaining budget. Blocking the calling thread is fine
 * because the project runs on virtual threads.
 */
class RetryingStreamingChatModel(
    private val delegate: StreamingChatModel,
    private val properties: LlmRetryProperties
) : StreamingChatModel {

    override fun chat(request: ChatRequest, handler: StreamingChatResponseHandler) {
        var attempt = 1
        var delay = properties.initialDelayMs

        while (true) {
            when (val outcome = runSingleAttempt(request, handler)) {
                is Outcome.Success -> return
                is Outcome.NotRetryable -> {
                    handler.onError(outcome.error)
                    return
                }
                is Outcome.Retryable -> {
                    if (attempt >= properties.maxAttempts) {
                        log.warn {
                            "Streaming LLM retries exhausted after $attempt attempt(s): " +
                                "${outcome.error.javaClass.simpleName}: ${outcome.error.message}"
                        }
                        handler.onError(outcome.error)
                        return
                    }
                    val nextSleepMs = applyJitter(delay)
                    val remainingBudget = LlmRetryEventEmitter.remainingBudgetMs()
                    if (remainingBudget != null && remainingBudget < nextSleepMs) {
                        // Even the backoff sleep would push us past the agent's wall-clock
                        // budget — give up rather than retrying into a known overrun.
                        log.warn {
                            "Streaming LLM retry budget exhausted before attempt ${attempt + 1} " +
                                "(remaining=${remainingBudget}ms, next sleep=${nextSleepMs}ms): " +
                                "${outcome.error.javaClass.simpleName}: ${outcome.error.message}"
                        }
                        handler.onError(outcome.error)
                        return
                    }
                    log.warn {
                        "Retrying streaming LLM call (attempt ${attempt + 1}/${properties.maxAttempts}): " +
                            "${outcome.error.javaClass.simpleName}: ${outcome.error.message}"
                    }
                    LlmRetryEventEmitter.emit(attempt + 1, properties.maxAttempts, LlmRetryClassifier.safeDescription(outcome.error))
                    Thread.sleep(nextSleepMs)
                    // Re-check after the sleep: the boundary case `remainingBudget == nextSleepMs`
                    // slips through the pre-sleep check. Issuing another delegate.chat(...) here
                    // would start a real provider request even though the latch wait would
                    // immediately time out — and because LangChain4j has no cancellation handle,
                    // that request would keep running and burn tokens past the agent deadline.
                    val budgetAfterSleep = LlmRetryEventEmitter.remainingBudgetMs()
                    if (budgetAfterSleep != null && budgetAfterSleep <= 0) {
                        log.warn {
                            "Streaming LLM retry budget expired during backoff sleep before attempt ${attempt + 1} " +
                                "(remaining=${budgetAfterSleep}ms): " +
                                "${outcome.error.javaClass.simpleName}: ${outcome.error.message}"
                        }
                        handler.onError(outcome.error)
                        return
                    }
                    delay = (delay.toDouble() * properties.delayFactor).toLong()
                        .coerceAtMost(properties.maxDelayMs)
                    attempt++
                }
            }
        }
    }

    /**
     * Runs a single streaming attempt. Partial chunks are forwarded immediately;
     * completion and error callbacks are gated by `outcomeLatched` so only the
     * first one wins (guarding against buggy delegates that fire both). The
     * latch wait is bounded by `attemptTimeoutMs` so a delegate that never
     * invokes any callback surfaces as a [SocketTimeoutException] instead of
     * hanging — but a latch-timeout is NotRetryable, since the original request
     * is still in flight with no cancellation handle and a retry would double
     * Anthropic spend. Real `onError` callbacks remain retryable.
     */
    private fun runSingleAttempt(
        request: ChatRequest,
        userHandler: StreamingChatResponseHandler
    ): Outcome {
        val firstChunkEmitted = AtomicBoolean(false)
        val outcomeLatched = AtomicBoolean(false)
        val errorRef = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)

        val wrapped = object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                // Drop late chunks from an abandoned attempt. Once the outcome is
                // latched, the underlying request may still emit chunks — suppress
                // them so they don't leak into a retry attempt or follow an already-
                // emitted final response. LangChain4j has no cancellation handle, so
                // suppressing at the decorator boundary is the best we can do.
                if (outcomeLatched.get()) return
                firstChunkEmitted.set(true)
                userHandler.onPartialResponse(partialResponse)
            }

            override fun onCompleteResponse(completeResponse: ChatResponse) {
                if (outcomeLatched.compareAndSet(false, true)) {
                    userHandler.onCompleteResponse(completeResponse)
                    latch.countDown()
                }
            }

            override fun onError(error: Throwable) {
                if (outcomeLatched.compareAndSet(false, true)) {
                    errorRef.set(error)
                    latch.countDown()
                }
            }
        }

        try {
            delegate.chat(request, wrapped)
        } catch (e: Exception) {
            // Some implementations may throw synchronously instead of invoking onError.
            // Errors (Throwable subtypes) are intentionally not caught — they indicate
            // JVM-level failures that should propagate.
            if (outcomeLatched.compareAndSet(false, true)) {
                errorRef.set(e)
                latch.countDown()
            }
        }

        // Cap the wait by the smaller of the per-attempt timeout and the remaining
        // agent budget. Without this, a hung delegate can hold the calling thread
        // for the full attemptTimeoutMs (e.g. 120s) even when the agent budget has
        // already been exhausted, breaking the wall-clock contract of
        // `agent.tool-execution-timeout-seconds`.
        val effectiveTimeoutMs = minOf(
            properties.attemptTimeoutMs,
            LlmRetryEventEmitter.remainingBudgetMs() ?: properties.attemptTimeoutMs
        ).coerceAtLeast(0)
        val latchTimedOut = !latch.await(effectiveTimeoutMs, TimeUnit.MILLISECONDS)
        if (latchTimedOut) {
            // Delegate never invoked any callback within the effective timeout — bound
            // the calling thread by synthesising a SocketTimeoutException, but mark the
            // outcome as NotRetryable below so we don't issue a second concurrent request.
            if (outcomeLatched.compareAndSet(false, true)) {
                errorRef.set(SocketTimeoutException("Streaming LLM call did not complete within ${effectiveTimeoutMs}ms"))
            }
        }

        val error = errorRef.get() ?: return Outcome.Success
        return when {
            firstChunkEmitted.get() -> Outcome.NotRetryable(error)
            // The original request is still in flight on a background thread, and we
            // have no cancellation handle — retrying would double Anthropic spend and
            // any provider-side effects. See class docs.
            latchTimedOut -> Outcome.NotRetryable(error)
            !LlmRetryClassifier.isRetryable(error) -> Outcome.NotRetryable(error)
            else -> Outcome.Retryable(error)
        }
    }

    /**
     * Applies symmetric proportional jitter to a delay. The result is uniformly
     * distributed in `[delayMs - delayMs * jitterFactor, delayMs + delayMs * jitterFactor]`,
     * clamped to a non-negative value. Returns the input unchanged when jitter
     * is disabled.
     */
    private fun applyJitter(delayMs: Long): Long {
        val jitterRange = (delayMs.toDouble() * properties.jitterFactor).toLong()
        if (jitterRange <= 0) return delayMs
        val offset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1)
        return (delayMs + offset).coerceAtLeast(0)
    }

    /**
     * Result of a single streaming attempt: success, an error that should be retried,
     * or an error that should be propagated to the user without further attempts.
     */
    private sealed class Outcome {
        object Success : Outcome()
        data class Retryable(val error: Throwable) : Outcome()
        data class NotRetryable(val error: Throwable) : Outcome()
    }
}
