package io.robothouse.agent.model

/**
 * Request body for testing an HTTP tool with sample arguments.
 */
data class TestHttpToolRequest(
    val arguments: Map<String, Any> = emptyMap()
)
