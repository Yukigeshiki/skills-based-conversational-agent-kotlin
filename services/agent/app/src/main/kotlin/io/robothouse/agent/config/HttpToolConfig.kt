package io.robothouse.agent.config

import dev.failsafe.FailsafeExecutor
import io.robothouse.agent.util.FailsafeProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

/**
 * Properties for the http tool HTTP client.
 */
@ConfigurationProperties(prefix = "http-tool.http")
data class HttpToolHttpProperties(
    val connectTimeoutSeconds: Long
)

/**
 * Properties for the http tool failsafe resilience policies.
 */
@ConfigurationProperties(prefix = "http-tool.failsafe")
data class HttpToolFailsafeProperties(
    val retryDelaySeconds: Long,
    val retryMaxRetries: Int,
    val circuitBreakerFailureThreshold: Int,
    val circuitBreakerDelaySeconds: Long,
    val circuitBreakerSuccessThreshold: Int,
    val rateLimiterMaxExecutions: Int,
    val rateLimiterPeriodSeconds: Long,
    val rateLimiterMaxWaitTimeSeconds: Long,
    val bulkheadMaxConcurrency: Int,
    val bulkheadMaxWaitTimeSeconds: Long
)

/**
 * Configures HTTP client and failsafe resilience beans for http tool
 * execution.
 */
@Configuration
@EnableConfigurationProperties(HttpToolHttpProperties::class, HttpToolFailsafeProperties::class)
class HttpToolConfig {

    /**
     * Shared HTTP client for http tool endpoint calls.
     */
    @Bean
    @Qualifier("httpToolHttpClient")
    fun httpToolHttpClient(properties: HttpToolHttpProperties): HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds))
            // Redirects are always disabled — following redirects on external tool
            // URLs is an SSRF vector: an attacker-controlled endpoint can 302 to an
            // internal IP after the URL has passed SafeUrlValidator's blocklist check.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    /**
     * Failsafe executor for http tool HTTP calls, configured with retry,
     * circuit breaker, rate limiter, and bulkhead policies.
     */
    @Bean
    @Qualifier("httpToolFailsafe")
    fun httpToolFailsafe(properties: HttpToolFailsafeProperties): FailsafeExecutor<Any> =
        FailsafeProvider.builder()
            .withRetryConfig(
                delay = Duration.ofSeconds(properties.retryDelaySeconds),
                maxRetries = properties.retryMaxRetries
            )
            .withCircuitBreakerConfig(
                failureThreshold = properties.circuitBreakerFailureThreshold,
                delay = Duration.ofSeconds(properties.circuitBreakerDelaySeconds),
                successThreshold = properties.circuitBreakerSuccessThreshold
            )
            .withRateLimiterConfig(
                maxExecutions = properties.rateLimiterMaxExecutions,
                period = Duration.ofSeconds(properties.rateLimiterPeriodSeconds),
                maxWaitTime = Duration.ofSeconds(properties.rateLimiterMaxWaitTimeSeconds)
            )
            .withBulkheadConfig(
                maxConcurrency = properties.bulkheadMaxConcurrency,
                maxWaitTime = Duration.ofSeconds(properties.bulkheadMaxWaitTimeSeconds)
            )
            .build()
}
