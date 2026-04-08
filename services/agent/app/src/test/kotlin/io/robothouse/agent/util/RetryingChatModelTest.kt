package io.robothouse.agent.util

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import io.robothouse.agent.config.LlmRetryProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

class RetryingChatModelTest {

    private val fastRetryProperties = LlmRetryProperties(
        maxAttempts = 4,
        initialDelayMs = 1,
        maxDelayMs = 5,
        delayFactor = 2.0,
        jitterFactor = 0.0,
        attemptTimeoutMs = 5000
    )

    private fun chatRequest(message: String): ChatRequest =
        ChatRequest.builder().messages(UserMessage.from(message)).build()

    private fun chatResponse(text: String): ChatResponse =
        ChatResponse.builder().aiMessage(AiMessage.from(text)).build()

    /** Stub model that returns fixed responses or throws based on a behaviour function. */
    private class StubChatModel(
        val behaviour: (Int) -> ChatResponse
    ) : ChatModel {
        val invocations = AtomicInteger(0)
        override fun doChat(request: ChatRequest): ChatResponse {
            val attempt = invocations.incrementAndGet()
            return behaviour(attempt)
        }
    }

    @Test
    fun `successful first call invokes delegate exactly once`() {
        val stub = StubChatModel { chatResponse("hello") }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val response = retrying.chat(chatRequest("hi"))

        assertEquals("hello", response.aiMessage().text())
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retryable failure then success retries once and returns success`() {
        val stub = StubChatModel { attempt ->
            if (attempt == 1) throw SocketException("transient network error")
            chatResponse("recovered")
        }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val response = retrying.chat(chatRequest("hi"))

        assertEquals("recovered", response.aiMessage().text())
        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `always-failing retryable error exhausts attempts and throws underlying cause`() {
        val originalError = SocketException("permanent network outage")
        val stub = StubChatModel { throw originalError }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val thrown = assertThrows<IOException> {
            retrying.chat(chatRequest("hi"))
        }

        assertSame(originalError, thrown)
        assertEquals(fastRetryProperties.maxAttempts, stub.invocations.get())
    }

    @Test
    fun `non-retryable exception fails fast without retrying`() {
        val originalError = IllegalArgumentException("bad request")
        val stub = StubChatModel { throw originalError }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val thrown = assertThrows<IllegalArgumentException> {
            retrying.chat(chatRequest("hi"))
        }

        assertSame(originalError, thrown)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `generic RuntimeException without retryable indicators is not retried`() {
        val originalError = RuntimeException("LLM service unavailable")
        val stub = StubChatModel { throw originalError }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val thrown = assertThrows<RuntimeException> {
            retrying.chat(chatRequest("hi"))
        }

        assertSame(originalError, thrown)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `RuntimeException with rate limit message is retried`() {
        val stub = StubChatModel { attempt ->
            if (attempt < 3) throw RuntimeException("rate limit exceeded")
            chatResponse("done")
        }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val response = retrying.chat(chatRequest("hi"))

        assertEquals("done", response.aiMessage().text())
        assertEquals(3, stub.invocations.get())
    }

    @Test
    fun `maxAttempts of 1 disables retries`() {
        val noRetryProperties = fastRetryProperties.copy(maxAttempts = 1)
        val originalError = SocketException("transient")
        val stub = StubChatModel { throw originalError }
        val retrying = RetryingChatModel(stub, noRetryProperties)

        val thrown = assertThrows<IOException> {
            retrying.chat(chatRequest("hi"))
        }

        assertSame(originalError, thrown)
        assertEquals(1, stub.invocations.get())
    }

    /**
     * Stub that overrides `chat` directly (mirroring AnthropicChatModel) instead
     * of `doChat`. The decorator must call `delegate.chat(...)` so this kind of
     * delegate works — calling `delegate.doChat(...)` would fall through to the
     * interface default `RuntimeException("Not implemented")`.
     */
    private class ChatOverridingStubModel : ChatModel {
        val invocations = AtomicInteger(0)
        override fun chat(request: ChatRequest): ChatResponse {
            invocations.incrementAndGet()
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build()
        }
    }

    @Test
    fun `decorator works with delegate that overrides chat directly (mirrors AnthropicChatModel)`() {
        val stub = ChatOverridingStubModel()
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        val response = retrying.chat(chatRequest("hi"))

        assertEquals("ok", response.aiMessage().text())
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retries are aborted early when the agent budget is exhausted`() {
        val stub = StubChatModel { throw SocketException("transient") }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        try {
            // Install a budget supplier that reports zero remaining time. The decorator
            // should abort retries before the next sleep and propagate the error after
            // a single attempt rather than running through maxAttempts.
            LlmRetryEventEmitter.setBudgetSupplier { 0L }
            assertThrows<SocketException> { retrying.chat(chatRequest("hi")) }
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        // The decorator stops after the first failure rather than exhausting all
        // maxAttempts (which would be 4 with fastRetryProperties).
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retries continue normally when budget supplier reports time remaining`() {
        var attemptCount = 0
        val stub = StubChatModel { attempt ->
            attemptCount = attempt
            if (attempt < 3) throw SocketException("transient")
            chatResponse("recovered")
        }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        try {
            LlmRetryEventEmitter.setBudgetSupplier { 60_000L }  // plenty of budget
            val response = retrying.chat(chatRequest("hi"))
            assertEquals("recovered", response.aiMessage().text())
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertEquals(3, attemptCount)
    }

    @Test
    fun `retries are aborted when the next backoff sleep would push past the deadline`() {
        // The previous Failsafe-based implementation only aborted when remaining <= 0.
        // With budget=100ms and a 1s configured backoff, the old code would sleep 1s
        // and then attempt again — already 900ms past the deadline. The fix is to
        // compare remaining against the next sleep duration: if the sleep alone
        // exceeds the budget, abort immediately.
        val slowBackoffProps = LlmRetryProperties(
            maxAttempts = 4,
            initialDelayMs = 1000,  // 1 second backoff
            maxDelayMs = 30000,
            delayFactor = 2.0,
            jitterFactor = 0.0,
            attemptTimeoutMs = 5000
        )
        val stub = StubChatModel { throw SocketException("transient") }
        val retrying = RetryingChatModel(stub, slowBackoffProps)

        try {
            LlmRetryEventEmitter.setBudgetSupplier { 100L }  // 100ms — far less than 1s sleep
            val start = System.currentTimeMillis()
            assertThrows<SocketException> { retrying.chat(chatRequest("hi")) }
            val elapsed = System.currentTimeMillis() - start
            // Decorator must NOT have slept the full 1s before giving up
            assertTrue(
                elapsed < 500,
                "expected immediate abort (no 1s sleep), elapsed=${elapsed}ms"
            )
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        // Only the first attempt should run; the budget check before the sleep
        // prevents the second attempt from being scheduled.
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retries proceed when only the next backoff sleep fits the remaining budget`() {
        // The check only requires that the backoff sleep itself fits within the
        // remaining budget. The in-flight call is bounded by LangChain4j's HTTP
        // timeout, and any overshoot of the agent budget is caught by the agent
        // loop's post-call checkTimeout — so the decorator does not reserve the
        // full per-call timeout in advance.
        val tightBudgetProps = LlmRetryProperties(
            maxAttempts = 4,
            initialDelayMs = 10,
            maxDelayMs = 30_000,
            delayFactor = 2.0,
            jitterFactor = 0.0,
            attemptTimeoutMs = 5000
        )
        val stub = StubChatModel { attempt ->
            if (attempt < 2) throw SocketException("transient")
            chatResponse("recovered")
        }
        val retrying = RetryingChatModel(stub, tightBudgetProps)

        try {
            // 1s budget — comfortably above the 10ms backoff sleep
            LlmRetryEventEmitter.setBudgetSupplier { 1_000L }
            val response = retrying.chat(chatRequest("hi"))
            assertEquals("recovered", response.aiMessage().text())
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `retries are admitted even when remaining budget is smaller than the per-call HTTP timeout`() {
        // Regression for the [P3] fix: with production defaults, modelTimeoutMs and the
        // agent budget are both 120s, so an earlier conservative check
        // (`remainingBudget < nextSleepMs + modelTimeoutMs`) would refuse every retry
        // after any fast first failure. The decorator must admit the retry as long as
        // the backoff sleep itself fits the remaining budget — overshoot is enforced
        // by the agent loop's post-call checkTimeout, not preemptively here.
        val productionLikeProps = LlmRetryProperties(
            maxAttempts = 4,
            initialDelayMs = 1000,
            maxDelayMs = 30_000,
            delayFactor = 2.0,
            jitterFactor = 0.0,
            attemptTimeoutMs = 60_000
        )
        val stub = StubChatModel { attempt ->
            if (attempt < 2) throw SocketException("transient")
            chatResponse("recovered")
        }
        val retrying = RetryingChatModel(stub, productionLikeProps)

        try {
            // Budget=119_800ms (120s minus a fast first failure), modelTimeoutMs=60_000ms.
            // The old check would have computed worstCase = 1000 + 60_000 = 61_000ms and,
            // had the budget been even slightly tighter, refused the retry. The new check
            // only requires that the 1000ms sleep fits the 119_800ms budget, so retry
            // proceeds and the second attempt succeeds.
            LlmRetryEventEmitter.setBudgetSupplier { 119_800L }
            val response = retrying.chat(chatRequest("hi"))
            assertEquals("recovered", response.aiMessage().text())
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `retries are aborted when the budget expires during the backoff sleep`() {
        // Boundary case: pre-sleep check passes, but by the time the sleep returns the
        // deadline has been reached. Without the post-sleep recheck, the decorator would
        // issue another delegate.chat(...) and pin the calling thread for up to
        // modelTimeoutMs past the deadline.
        //
        // Use a counter-based budget supplier so the test is deterministic regardless
        // of CI scheduling. Each retry decision queries the budget exactly twice:
        //   - call #1: pre-sleep check (must return >= nextSleepMs to proceed)
        //   - call #2: post-sleep check (must return <= 0 to abort)
        val budgetCallCount = AtomicInteger(0)
        val stub = StubChatModel { throw SocketException("transient") }
        val retrying = RetryingChatModel(stub, fastRetryProperties)

        try {
            LlmRetryEventEmitter.setBudgetSupplier {
                if (budgetCallCount.incrementAndGet() == 1) 100L else 0L
            }
            assertThrows<SocketException> { retrying.chat(chatRequest("hi")) }
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        // Exactly one attempt: the post-sleep budget check aborts before the second
        // delegate.chat(...) can be issued. Without the recheck this would be 4
        // (the full maxAttempts of fastRetryProperties).
        assertEquals(1, stub.invocations.get())
        // Sanity-check the supplier was called exactly twice for the retry decision.
        assertEquals(2, budgetCallCount.get())
    }
}
