package io.robothouse.agent.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.io.FileNotFoundException
import java.net.BindException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException

class LlmRetryClassifierTest {

    /** Test stand-in for an SDK-thrown HTTP exception with a numeric status code. */
    private class FakeHttpException(message: String, val statusCode: Int) : RuntimeException(message)

    /** Test stand-in for an SDK class with a getStatusCode getter. */
    private class FakeHttpStatusException(message: String, private val status: Int) : RuntimeException(message) {
        @Suppress("unused")
        fun getStatusCode(): Int = status
    }

    @Test
    fun `SocketException is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(SocketException("connection broken")))
    }

    @Test
    fun `SocketTimeoutException is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `UnknownHostException is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(UnknownHostException("DNS resolution failed")))
    }

    @Test
    fun `HttpTimeoutException is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(HttpTimeoutException("response timeout")))
    }

    @Test
    fun `ConnectException is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(ConnectException("connection refused")))
    }

    @Test
    fun `non-transient SocketException subclasses are not retryable`() {
        // Local config / routing failures, not provider issues — retrying them only
        // burns through the backoff budget. The blanket SocketException catch must
        // not admit them.
        assertFalse(LlmRetryClassifier.isRetryable(BindException("address already in use")))
        assertFalse(LlmRetryClassifier.isRetryable(NoRouteToHostException("no route to host")))
        assertFalse(LlmRetryClassifier.isRetryable(PortUnreachableException("ICMP port unreachable")))
    }

    @Test
    fun `BindException with retryable keyword in message is still not retryable`() {
        // Defence-in-depth: even if a non-transient subclass somehow has a message
        // that matches a retryable keyword, the explicit carve-out must win.
        assertFalse(LlmRetryClassifier.isRetryable(BindException("connection reset while binding")))
    }

    @Test
    fun `non-transient IOException subclasses are not retryable`() {
        assertFalse(LlmRetryClassifier.isRetryable(MalformedURLException("bad URL")))
        assertFalse(LlmRetryClassifier.isRetryable(FileNotFoundException("missing file")))
        assertFalse(LlmRetryClassifier.isRetryable(EOFException("unexpected end")))
    }

    @Test
    fun `HttpException with retryable status code is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(FakeHttpException("rate limited", 429)))
        assertTrue(LlmRetryClassifier.isRetryable(FakeHttpException("server overloaded", 529)))
        assertTrue(LlmRetryClassifier.isRetryable(FakeHttpException("bad gateway", 502)))
    }

    @Test
    fun `HttpException with non-retryable status code is not retryable by status alone`() {
        // 401 doesn't match any keyword either, so this should be false
        assertFalse(LlmRetryClassifier.isRetryable(FakeHttpException("unauthorized", 401)))
        assertFalse(LlmRetryClassifier.isRetryable(FakeHttpException("forbidden", 403)))
        assertFalse(LlmRetryClassifier.isRetryable(FakeHttpException("bad request", 400)))
    }

    @Test
    fun `HttpStatusException via getStatusCode getter is detected`() {
        assertTrue(LlmRetryClassifier.isRetryable(FakeHttpStatusException("overloaded", 503)))
    }

    @Test
    fun `generic RuntimeException without retryable indicators is not retryable`() {
        assertFalse(LlmRetryClassifier.isRetryable(RuntimeException("LLM service unavailable")))
        assertFalse(LlmRetryClassifier.isRetryable(IllegalStateException("bad state")))
        assertFalse(LlmRetryClassifier.isRetryable(NullPointerException()))
    }

    @Test
    fun `RuntimeException with rate limit keyword is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(RuntimeException("Rate limit exceeded for organization")))
    }

    @Test
    fun `RuntimeException with overloaded keyword is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(RuntimeException("Anthropic API is overloaded, try later")))
    }

    @Test
    fun `RuntimeException containing 503 in message is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(RuntimeException("HTTP 503 Service Unavailable")))
    }

    @Test
    fun `RuntimeException with timeout keyword is retryable`() {
        assertTrue(LlmRetryClassifier.isRetryable(RuntimeException("Request timed out after 30s")))
    }

    @Test
    fun `wrapped retryable exception in cause chain is detected`() {
        val root = SocketTimeoutException("read timed out")
        val wrapper = RuntimeException("wrapping", root)
        assertTrue(LlmRetryClassifier.isRetryable(wrapper))
    }

    @Test
    fun `deeply nested cause is detected within max depth`() {
        // Depth 4 — within MAX_CAUSE_DEPTH=5
        val root = SocketException("network error")
        val l1 = RuntimeException("l1", root)
        val l2 = RuntimeException("l2", l1)
        val l3 = RuntimeException("l3", l2)
        val l4 = RuntimeException("l4", l3)
        assertTrue(LlmRetryClassifier.isRetryable(l4))
    }

    @Test
    fun `non-retryable wrapper around non-retryable cause stays non-retryable`() {
        val root = IllegalArgumentException("bad input")
        val wrapper = RuntimeException("wrapping", root)
        assertFalse(LlmRetryClassifier.isRetryable(wrapper))
    }

    // ---- safeDescription ----

    @Test
    fun `safeDescription never returns the raw throwable message for transport errors`() {
        // The raw messages here would leak provider response bodies, transport details,
        // or DNS responses to the chat activity log. The sanitiser must replace them
        // with category labels.
        val rawSocketMessage = "Connection reset by peer; raw response: {\"error\":\"internal\",\"trace\":\"abc-123\"}"
        assertEquals("Network connection issue", LlmRetryClassifier.safeDescription(SocketException(rawSocketMessage)))
        assertEquals("Request timed out", LlmRetryClassifier.safeDescription(SocketTimeoutException("read timed out after 60s, body=...")))
        assertEquals("Request timed out", LlmRetryClassifier.safeDescription(HttpTimeoutException("response timeout")))
        assertEquals("DNS resolution failed", LlmRetryClassifier.safeDescription(UnknownHostException("api.anthropic.internal: no such host")))
        assertEquals("Network connection issue", LlmRetryClassifier.safeDescription(ConnectException("connection refused to 10.0.0.1:443")))
    }

    @Test
    fun `safeDescription maps retryable HTTP status codes to public labels`() {
        assertEquals("Rate limited (HTTP 429)", LlmRetryClassifier.safeDescription(FakeHttpException("body", 429)))
        assertEquals("Service unavailable (HTTP 503)", LlmRetryClassifier.safeDescription(FakeHttpException("body", 503)))
        assertEquals("Provider overloaded (HTTP 529)", LlmRetryClassifier.safeDescription(FakeHttpException("body", 529)))
        assertEquals("Bad gateway (HTTP 502)", LlmRetryClassifier.safeDescription(FakeHttpException("body", 502)))
        assertEquals("Gateway timeout (HTTP 504)", LlmRetryClassifier.safeDescription(FakeHttpException("body", 504)))
    }

    @Test
    fun `safeDescription falls back to keyword categorisation when no transport type matches`() {
        // The raw message contains the keyword but also contains internal details that
        // must NOT leak. Only the category label should be returned.
        assertEquals(
            "Rate limited",
            LlmRetryClassifier.safeDescription(RuntimeException("Rate limit exceeded for org_id=internal-prod-7"))
        )
        assertEquals(
            "Provider overloaded",
            LlmRetryClassifier.safeDescription(RuntimeException("Anthropic API is overloaded; backend trace=xyz"))
        )
        assertEquals(
            "Service unavailable",
            LlmRetryClassifier.safeDescription(RuntimeException("HTTP 503 from upstream lb-internal-3.k8s.local"))
        )
        assertEquals(
            "Request timed out",
            LlmRetryClassifier.safeDescription(RuntimeException("Read timed out waiting for upstream-prod-7.cluster.local"))
        )
    }

    @Test
    fun `safeDescription returns generic label when nothing matches`() {
        assertEquals("Transient error", LlmRetryClassifier.safeDescription(RuntimeException("something weird")))
        assertEquals("Transient error", LlmRetryClassifier.safeDescription(IllegalStateException("bad state")))
    }

    @Test
    fun `safeDescription walks the cause chain to find a category`() {
        val root = SocketTimeoutException("read timed out: secret-internal-detail")
        val l1 = RuntimeException("wrapped: lb-prod-3.cluster.local error", root)
        val l2 = RuntimeException("further wrapped", l1)
        // Must surface the category from the cause, never the wrapper messages.
        assertEquals("Request timed out", LlmRetryClassifier.safeDescription(l2))
    }

    @Test
    fun `safeDescription does not surface non-retryable subclass details`() {
        // BindException is non-transient — categorise() returns null for it. The walker
        // continues up the chain; if nothing else matches, the generic label is returned
        // rather than leaking the BindException's message.
        val bindError = BindException("address already in use: 0.0.0.0:443 (internal-pool)")
        assertEquals("Transient error", LlmRetryClassifier.safeDescription(bindError))
    }
}
