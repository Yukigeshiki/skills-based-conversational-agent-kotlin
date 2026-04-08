package io.robothouse.agent.util

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.robothouse.agent.config.LlmRetryProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RetryingStreamingChatModelTest {

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

    /** Captures all callbacks invoked on the wrapped streaming handler. */
    private class CapturingHandler : StreamingChatResponseHandler {
        val partials = mutableListOf<String>()
        var completeResponse: ChatResponse? = null
        var error: Throwable? = null

        override fun onPartialResponse(partialResponse: String) {
            partials.add(partialResponse)
        }

        override fun onCompleteResponse(completeResponse: ChatResponse) {
            this.completeResponse = completeResponse
        }

        override fun onError(error: Throwable) {
            this.error = error
        }
    }

    /** Stub that simulates streaming behaviour by attempt number. */
    private class StubStreamingChatModel(
        val behaviour: (Int, StreamingChatResponseHandler) -> Unit
    ) : StreamingChatModel {
        val invocations = AtomicInteger(0)
        override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
            val attempt = invocations.incrementAndGet()
            behaviour(attempt, handler)
        }
    }

    @Test
    fun `successful stream forwards all callbacks and does not retry`() {
        val stub = StubStreamingChatModel { _, handler ->
            handler.onPartialResponse("Hello")
            handler.onPartialResponse(" world")
            handler.onCompleteResponse(chatResponse("Hello world"))
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(listOf("Hello", " world"), handler.partials)
        assertNotNull(handler.completeResponse)
        assertEquals("Hello world", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retryable error before first chunk is retried until success`() {
        val stub = StubStreamingChatModel { attempt, handler ->
            if (attempt < 3) {
                handler.onError(SocketException("connection refused"))
            } else {
                handler.onPartialResponse("recovered")
                handler.onCompleteResponse(chatResponse("recovered"))
            }
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(listOf("recovered"), handler.partials)
        assertEquals("recovered", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(3, stub.invocations.get())
    }

    @Test
    fun `retryable error before first chunk exhausts retries and forwards error`() {
        val originalError = SocketException("permanent outage")
        val stub = StubStreamingChatModel { _, handler -> handler.onError(originalError) }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(emptyList<String>(), handler.partials)
        assertNull(handler.completeResponse)
        assertSame(originalError, handler.error)
        assertEquals(fastRetryProperties.maxAttempts, stub.invocations.get())
    }

    @Test
    fun `non-retryable error before first chunk fails fast without retrying`() {
        val originalError = IllegalArgumentException("bad request")
        val stub = StubStreamingChatModel { _, handler -> handler.onError(originalError) }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(emptyList<String>(), handler.partials)
        assertNull(handler.completeResponse)
        assertSame(originalError, handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `error after first chunk is forwarded without retry`() {
        val originalError = SocketException("connection reset mid-stream")
        val stub = StubStreamingChatModel { _, handler ->
            handler.onPartialResponse("partial output")
            handler.onError(originalError)
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(listOf("partial output"), handler.partials)
        assertNull(handler.completeResponse)
        assertSame(originalError, handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `synchronous exception thrown by delegate is treated as error`() {
        val originalError = SocketException("synchronous failure")
        val stub = StubStreamingChatModel { _, _ -> throw originalError }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties.copy(maxAttempts = 2))
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertSame(originalError, handler.error)
        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `maxAttempts of 1 disables retries`() {
        val noRetryProperties = fastRetryProperties.copy(maxAttempts = 1)
        val originalError = SocketException("transient")
        val stub = StubStreamingChatModel { _, handler -> handler.onError(originalError) }
        val retrying = RetryingStreamingChatModel(stub, noRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertSame(originalError, handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `streamAndBlock-style user handler counts down its latch on success`() {
        // Mirrors the production caller in AgentGraphBuilder.streamAndBlock — the user
        // handler holds its own CountDownLatch and references for response and error.
        // Verifies that after a successful retry the user latch is at zero so the caller's
        // wall-clock timeout check is bypassed.
        val stub = StubStreamingChatModel { attempt, handler ->
            if (attempt == 1) {
                handler.onError(SocketException("transient"))
            } else {
                handler.onPartialResponse("Hello")
                handler.onCompleteResponse(chatResponse("Hello world"))
            }
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)

        val userLatch = CountDownLatch(1)
        val responseRef = AtomicReference<ChatResponse>()
        val errorRef = AtomicReference<Throwable>()
        val partials = mutableListOf<String>()

        val streamAndBlockStyleHandler = object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                partials.add(partialResponse)
            }
            override fun onCompleteResponse(completeResponse: ChatResponse) {
                responseRef.set(completeResponse)
                userLatch.countDown()
            }
            override fun onError(error: Throwable) {
                errorRef.set(error)
                userLatch.countDown()
            }
        }

        retrying.chat(chatRequest("hi"), streamAndBlockStyleHandler)

        // After the decorator returns, the user latch must already be counted down so
        // that streamAndBlock's `if (latch.count > 0L)` short-circuit fires correctly.
        assertEquals(0L, userLatch.count)
        assertNotNull(responseRef.get())
        assertEquals("Hello world", responseRef.get().aiMessage().text())
        assertNull(errorRef.get())
        assertEquals(listOf("Hello"), partials)
        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `buggy delegate calling onCompleteResponse then onError fires user callback only once`() {
        val stub = StubStreamingChatModel { _, handler ->
            handler.onCompleteResponse(chatResponse("done"))
            handler.onError(SocketException("late error"))
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // Only the first callback (onCompleteResponse) should be forwarded
        assertEquals("done", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `buggy delegate calling onError then onCompleteResponse forwards only the error`() {
        val originalError = SocketException("first error")
        val stub = StubStreamingChatModel { _, handler ->
            handler.onError(originalError)
            handler.onCompleteResponse(chatResponse("late completion"))
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties.copy(maxAttempts = 1))
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // Only the first callback (onError) should be processed
        assertNull(handler.completeResponse)
        assertSame(originalError, handler.error)
    }

    @Test
    fun `delegate that never invokes any callback times out and is NOT retried`() {
        // Regression: a latch-timeout (no callback before deadline) must NOT trigger a
        // retry. LangChain4j has no cancellation handle, so the original request is
        // still in flight on a background thread — issuing a second delegate.chat(...)
        // would produce two concurrent Anthropic requests for one logical call.
        val tinyTimeoutProps = fastRetryProperties.copy(attemptTimeoutMs = 30)
        val stub = StubStreamingChatModel { _, _ -> /* never invoke any callback */ }
        val retrying = RetryingStreamingChatModel(stub, tinyTimeoutProps)
        val handler = CapturingHandler()

        val start = System.currentTimeMillis()
        retrying.chat(chatRequest("hi"), handler)
        val elapsed = System.currentTimeMillis() - start

        // Exactly one attempt: the timeout fires, the user receives the synthetic
        // SocketTimeoutException, no retry is scheduled.
        assertNotNull(handler.error)
        assertTrue(handler.error is SocketTimeoutException)
        assertEquals(1, stub.invocations.get())
        assertTrue(elapsed >= 30, "expected at least 30ms elapsed (one attempt timeout), was ${elapsed}ms")
    }

    @Test
    fun `real onError callbacks remain retryable even after a previous attempt timed out`() {
        // Confirms that the latch-timeout fix does NOT regress the normal retry path:
        // an attempt that surfaces a real onError (proving the underlying request has
        // settled) is still retried as before.
        val stub = StubStreamingChatModel { attempt, handler ->
            if (attempt == 1) {
                handler.onError(SocketException("transient"))
            } else {
                handler.onPartialResponse("recovered")
                handler.onCompleteResponse(chatResponse("recovered"))
            }
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(listOf("recovered"), handler.partials)
        assertEquals("recovered", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(2, stub.invocations.get())
    }

    /**
     * Stub that overrides `chat` directly (mirroring AnthropicStreamingChatModel)
     * instead of `doChat`. The decorator must call `delegate.chat(...)` so this kind
     * of delegate works — calling `delegate.doChat(...)` would fall through to the
     * interface default `RuntimeException("Not implemented")`.
     */
    private class ChatOverridingStubStreamingModel : StreamingChatModel {
        val invocations = AtomicInteger(0)
        override fun chat(request: ChatRequest, handler: StreamingChatResponseHandler) {
            invocations.incrementAndGet()
            handler.onPartialResponse("ok")
            handler.onCompleteResponse(
                ChatResponse.builder().aiMessage(AiMessage.from("ok")).build()
            )
        }
    }

    @Test
    fun `decorator works with delegate that overrides chat directly (mirrors AnthropicStreamingChatModel)`() {
        val stub = ChatOverridingStubStreamingModel()
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        assertEquals(listOf("ok"), handler.partials)
        assertEquals("ok", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `late chunks after onError are suppressed`() {
        // Simulates a buggy delegate (or a slow underlying request whose chunks arrive
        // after the SDK has reported an error). The decorator must drop the late
        // chunks rather than forwarding them to the user handler.
        val stub = StubStreamingChatModel { _, handler ->
            handler.onError(SocketException("transient"))
            handler.onPartialResponse("late chunk 1")
            handler.onPartialResponse("late chunk 2")
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties.copy(maxAttempts = 1))
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // The user handler should only see the error — no late chunks
        assertEquals(emptyList<String>(), handler.partials)
        assertNull(handler.completeResponse)
        assertNotNull(handler.error)
    }

    @Test
    fun `late chunks after onCompleteResponse are suppressed`() {
        // Simulates a buggy delegate that emits more chunks after marking the response
        // complete. The decorator must drop them so the user doesn't see tokens after
        // the supposedly final response.
        val stub = StubStreamingChatModel { _, handler ->
            handler.onPartialResponse("first")
            handler.onCompleteResponse(chatResponse("first"))
            handler.onPartialResponse("after-complete chunk")
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // Only the legitimate first chunk should be forwarded
        assertEquals(listOf("first"), handler.partials)
        assertEquals("first", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
    }

    @Test
    fun `retries are aborted early when the agent budget cannot fit the next backoff`() {
        val stub = StubStreamingChatModel { _, handler -> handler.onError(SocketException("transient")) }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        try {
            // Budget supplier reports a remaining budget that is smaller than the next
            // backoff sleep (initialDelayMs=1ms with delayFactor=2.0 starts at 1ms).
            // The decorator should abort after the first failure.
            LlmRetryEventEmitter.setBudgetSupplier { 0L }
            retrying.chat(chatRequest("hi"), handler)
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertNotNull(handler.error)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `retries continue normally when budget supplier reports time remaining`() {
        val stub = StubStreamingChatModel { attempt, handler ->
            if (attempt < 3) {
                handler.onError(SocketException("transient"))
            } else {
                handler.onPartialResponse("recovered")
                handler.onCompleteResponse(chatResponse("recovered"))
            }
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        try {
            LlmRetryEventEmitter.setBudgetSupplier { 60_000L }  // plenty of budget
            retrying.chat(chatRequest("hi"), handler)
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertEquals(listOf("recovered"), handler.partials)
        assertEquals("recovered", handler.completeResponse?.aiMessage()?.text())
        assertEquals(3, stub.invocations.get())
    }

    @Test
    fun `attempt wait is bounded by remaining agent budget when smaller than attemptTimeoutMs`() {
        // Configure attempt timeout much larger than the agent budget. The decorator
        // should give up after roughly the budget rather than waiting the full
        // attemptTimeoutMs. Without the budget cap, a hung delegate would hold the
        // calling thread for 60 seconds despite a 50ms budget.
        val largeAttemptProps = fastRetryProperties.copy(
            maxAttempts = 1,
            attemptTimeoutMs = 60_000  // 60 seconds
        )
        val stub = StubStreamingChatModel { _, _ -> /* never invoke any callback */ }
        val retrying = RetryingStreamingChatModel(stub, largeAttemptProps)
        val handler = CapturingHandler()

        try {
            LlmRetryEventEmitter.setBudgetSupplier { 50L }  // 50ms — much less than 60s
            val start = System.currentTimeMillis()
            retrying.chat(chatRequest("hi"), handler)
            val elapsed = System.currentTimeMillis() - start
            assertTrue(
                elapsed < 5000,
                "expected wait bounded by budget (~50ms), was ${elapsed}ms"
            )
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        assertNotNull(handler.error)
        assertTrue(handler.error is SocketTimeoutException)
        assertEquals(1, stub.invocations.get())
    }

    @Test
    fun `attempt wait uses attemptTimeoutMs when no budget supplier installed`() {
        // Mirror the above scenario without a budget supplier. The decorator should
        // fall back to attemptTimeoutMs and time out at that bound.
        val tinyAttemptProps = fastRetryProperties.copy(maxAttempts = 1, attemptTimeoutMs = 30)
        val stub = StubStreamingChatModel { _, _ -> /* never invoke any callback */ }
        val retrying = RetryingStreamingChatModel(stub, tinyAttemptProps)
        val handler = CapturingHandler()

        // No budget supplier installed
        retrying.chat(chatRequest("hi"), handler)

        assertNotNull(handler.error)
        assertTrue(handler.error is SocketTimeoutException)
    }

    @Test
    fun `late chunks from a previously errored attempt do not leak into the retry`() {
        // Simulates the realistic scenario where attempt 1's underlying request reports
        // an error (so the decorator retries), but the original background stream is
        // still alive and emits late chunks after the retry has been scheduled. Those
        // late chunks must not interleave with attempt 2's output.
        val capturedHandlers = java.util.concurrent.ConcurrentLinkedQueue<StreamingChatResponseHandler>()
        val stub = StubStreamingChatModel { attempt, handler ->
            if (attempt == 1) {
                capturedHandlers.add(handler)
                handler.onError(SocketException("transient"))
            } else {
                handler.onPartialResponse("retry chunk")
                handler.onCompleteResponse(chatResponse("retry response"))
            }
        }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // Simulate attempt 1's abandoned underlying request producing late chunks
        capturedHandlers.forEach { abandoned ->
            abandoned.onPartialResponse("LEAKED late chunk")
        }

        // The user handler should only see attempt 2's output — no leaked chunks
        assertEquals(listOf("retry chunk"), handler.partials)
        assertEquals("retry response", handler.completeResponse?.aiMessage()?.text())
        assertNull(handler.error)
        assertEquals(2, stub.invocations.get())
    }

    @Test
    fun `latch-timeout does not start a second concurrent delegate call`() {
        // Regression for the [P2] fix: prove that when the latch times out without any
        // callback, the decorator does NOT issue a second delegate.chat(...) — which would
        // run concurrently with the still-in-flight original request and double the cost.
        val tinyTimeoutProps = fastRetryProperties.copy(maxAttempts = 4, attemptTimeoutMs = 20)
        val stub = StubStreamingChatModel { _, _ -> /* hang — no callback ever */ }
        val retrying = RetryingStreamingChatModel(stub, tinyTimeoutProps)
        val handler = CapturingHandler()

        retrying.chat(chatRequest("hi"), handler)

        // Even though maxAttempts=4, the decorator must have called the delegate exactly
        // once. A retry under the old behaviour would have run a second delegate.chat(...)
        // while the first request was still alive on a background thread.
        assertEquals(1, stub.invocations.get())
        assertNotNull(handler.error)
        assertTrue(handler.error is SocketTimeoutException)
    }

    @Test
    fun `streaming retries are aborted when the budget expires during the backoff sleep`() {
        // Boundary case: pre-sleep check passes, but by the time the sleep returns the
        // deadline has been reached. Without the post-sleep recheck, the decorator would
        // call delegate.chat(...) again — issuing a real provider request that immediately
        // times out at the latch but keeps running in the background, doubling token spend
        // past the agent deadline (LangChain4j has no cancellation handle).
        //
        // Use a counter-based budget supplier so the test is deterministic. Each retry
        // decision queries the budget exactly three times in this scenario:
        //   - call #1: inside runSingleAttempt for the latch effectiveTimeoutMs cap
        //   - call #2: pre-sleep check (must be >= nextSleepMs to proceed)
        //   - call #3: post-sleep check (must be <= 0 to abort)
        val budgetCallCount = AtomicInteger(0)
        val stub = StubStreamingChatModel { _, handler -> handler.onError(SocketException("transient")) }
        val retrying = RetryingStreamingChatModel(stub, fastRetryProperties)
        val handler = CapturingHandler()

        try {
            LlmRetryEventEmitter.setBudgetSupplier {
                if (budgetCallCount.incrementAndGet() <= 2) 100L else 0L
            }
            retrying.chat(chatRequest("hi"), handler)
        } finally {
            LlmRetryEventEmitter.clearBudgetSupplier()
        }

        // Exactly one delegate invocation: the post-sleep budget check aborts before the
        // second runSingleAttempt can issue another provider request. Without the
        // recheck this would be 4 (the full maxAttempts of fastRetryProperties).
        assertEquals(1, stub.invocations.get())
        assertNotNull(handler.error)
        // The supplier was queried exactly three times for the retry decision.
        assertEquals(3, budgetCallCount.get())
    }
}
