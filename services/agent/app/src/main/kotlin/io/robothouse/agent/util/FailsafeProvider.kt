package io.robothouse.agent.util

import dev.failsafe.Bulkhead
import dev.failsafe.CircuitBreaker
import dev.failsafe.Failsafe
import dev.failsafe.FailsafeExecutor
import dev.failsafe.RateLimiter
import dev.failsafe.RetryPolicy
import java.io.IOException
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * Provides resilience patterns (retry, circuit breaker, rate limiter, bulkhead)
 * using the Failsafe library with a fluent builder pattern.
 */
object FailsafeProvider {

    /**
     * Creates a new builder for configuring a [FailsafeExecutor].
     */
    fun builder() = Builder()

    class Builder {
        private var retryDelay = Duration.ofSeconds(1)
        private var retryMaxRetries = 3
        private var circuitBreakerFailureThreshold = 5
        private var circuitBreakerDelay = Duration.ofMinutes(1)
        private var circuitBreakerSuccessThreshold = 2
        private var rateLimiterMaxExecutions = 100
        private var rateLimiterPeriod = Duration.ofMinutes(1)
        private var rateLimiterMaxWaitTime = Duration.ofSeconds(2)
        private var bulkheadMaxConcurrency = 20
        private var bulkheadMaxWaitTime = Duration.ofSeconds(2)

        /**
         * Sets the delay between retry attempts.
         */
        fun retryDelay(delay: Duration) = apply { retryDelay = delay }

        /**
         * Sets the maximum number of retry attempts.
         */
        fun retryMaxRetries(maxRetries: Int) = apply { retryMaxRetries = maxRetries }

        /**
         * Sets the number of consecutive failures before the circuit opens.
         */
        fun circuitBreakerFailureThreshold(threshold: Int) = apply {
            circuitBreakerFailureThreshold = threshold
        }

        /**
         * Sets the delay before the circuit breaker transitions from open
         * to half-open.
         */
        fun circuitBreakerDelay(delay: Duration) = apply {
            circuitBreakerDelay = delay
        }

        /**
         * Sets the number of consecutive successes in half-open state
         * before the circuit closes.
         */
        fun circuitBreakerSuccessThreshold(threshold: Int) = apply {
            circuitBreakerSuccessThreshold = threshold
        }

        /**
         * Sets the maximum number of executions allowed within the rate
         * limiter period.
         */
        fun rateLimiterMaxExecutions(maxExecutions: Int) = apply {
            rateLimiterMaxExecutions = maxExecutions
        }

        /**
         * Sets the time window for the rate limiter.
         */
        fun rateLimiterPeriod(period: Duration) = apply {
            rateLimiterPeriod = period
        }

        /**
         * Sets how long a call will wait for a rate limiter permit before
         * failing.
         */
        fun rateLimiterMaxWaitTime(maxWaitTime: Duration) = apply {
            rateLimiterMaxWaitTime = maxWaitTime
        }

        /**
         * Sets the maximum number of concurrent executions allowed by
         * the bulkhead.
         */
        fun bulkheadMaxConcurrency(maxConcurrency: Int) = apply {
            bulkheadMaxConcurrency = maxConcurrency
        }

        /**
         * Sets how long a call will wait for a bulkhead permit before
         * failing.
         */
        fun bulkheadMaxWaitTime(maxWaitTime: Duration) = apply {
            bulkheadMaxWaitTime = maxWaitTime
        }

        /**
         * Convenience method to configure retry delay and max retries
         * in a single call.
         */
        fun withRetryConfig(delay: Duration, maxRetries: Int) = apply {
            retryDelay = delay
            retryMaxRetries = maxRetries
        }

        /**
         * Convenience method to configure all circuit breaker settings
         * in a single call.
         */
        fun withCircuitBreakerConfig(
            failureThreshold: Int,
            delay: Duration,
            successThreshold: Int
        ) = apply {
            circuitBreakerFailureThreshold = failureThreshold
            circuitBreakerDelay = delay
            circuitBreakerSuccessThreshold = successThreshold
        }

        /**
         * Convenience method to configure all rate limiter settings
         * in a single call.
         */
        fun withRateLimiterConfig(
            maxExecutions: Int,
            period: Duration,
            maxWaitTime: Duration
        ) = apply {
            rateLimiterMaxExecutions = maxExecutions
            rateLimiterPeriod = period
            rateLimiterMaxWaitTime = maxWaitTime
        }

        /**
         * Convenience method to configure all bulkhead settings in a
         * single call.
         */
        fun withBulkheadConfig(
            maxConcurrency: Int,
            maxWaitTime: Duration
        ) = apply {
            bulkheadMaxConcurrency = maxConcurrency
            bulkheadMaxWaitTime = maxWaitTime
        }

        /**
         * Builds the [FailsafeExecutor] with the configured retry policy,
         * circuit breaker, rate limiter, and bulkhead composed together.
         *
         * The retry policy handles [IOException] and [HttpTimeoutException],
         * retrying on transient failures while respecting the circuit breaker,
         * rate limiter, and bulkhead constraints.
         */
        fun build(): FailsafeExecutor<Any> {
            val retryPolicy = RetryPolicy.builder<Any>()
                .handle(IOException::class.java)
                .handle(HttpTimeoutException::class.java)
                .withDelay(retryDelay)
                .withMaxRetries(retryMaxRetries)
                .build()

            val circuitBreaker = CircuitBreaker.builder<Any>()
                .handle(IOException::class.java, HttpTimeoutException::class.java)
                .withFailureThreshold(circuitBreakerFailureThreshold)
                .withDelay(circuitBreakerDelay)
                .withSuccessThreshold(circuitBreakerSuccessThreshold)
                .build()

            val rateLimiter = RateLimiter.smoothBuilder<Any>(
                rateLimiterMaxExecutions.toLong(),
                rateLimiterPeriod
            )
                .withMaxWaitTime(rateLimiterMaxWaitTime)
                .build()

            val bulkhead = Bulkhead.builder<Any>(bulkheadMaxConcurrency)
                .withMaxWaitTime(bulkheadMaxWaitTime)
                .build()

            return Failsafe.with(retryPolicy)
                .compose(circuitBreaker)
                .compose(rateLimiter)
                .compose(bulkhead)
        }
    }
}
