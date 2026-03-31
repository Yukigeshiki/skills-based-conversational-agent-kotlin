package io.robothouse.agent.model

/**
 * Response from testing an HTTP tool, containing the HTTP call results.
 */
data class TestHttpToolResponse(
    val statusCode: Int,
    val body: String,
    val durationMs: Long,
    val truncated: Boolean
)
