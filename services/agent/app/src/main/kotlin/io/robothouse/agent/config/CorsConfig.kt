package io.robothouse.agent.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

/**
 * Global CORS configuration for all endpoints.
 *
 * Reads allowed origins from the `cors.allowed-origins` property, supporting
 * both exact URLs and wildcard patterns. Credentials are enabled for all
 * allowed origins and the `Request-ID` header is exposed so the browser
 * can read the echoed request ID from responses.
 */
@Configuration
class CorsConfig(
    @param:Value("\${cors.allowed-origins}") private val allowedOrigins: String
) {

    /**
     * Registers a global CORS filter that applies to all endpoints,
     * replacing per-controller `@CrossOrigin` annotations.
     */
    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration()

        if (allowedOrigins.isNotBlank()) {
            allowedOrigins.split(",").map { it.trim() }.forEach { origin ->
                if (origin.contains("*")) {
                    config.addAllowedOriginPattern(origin)
                } else {
                    config.addAllowedOrigin(origin)
                }
            }
        }

        config.allowCredentials = true
        config.addAllowedMethod("*")
        config.addAllowedHeader("*")
        config.addExposedHeader("Request-ID")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)

        return CorsFilter(source)
    }
}
