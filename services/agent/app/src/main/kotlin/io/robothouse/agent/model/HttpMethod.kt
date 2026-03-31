package io.robothouse.agent.model

import com.fasterxml.jackson.annotation.JsonValue

/**
 * The supported HTTP methods for HTTP tool endpoints.
 */
enum class HttpMethod(@get:JsonValue val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE")
}
