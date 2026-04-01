package io.robothouse.agent.config

import io.robothouse.agent.util.log
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.UUID
import java.util.regex.Pattern

/**
 * Request filter providing tracing context and request IDs for
 * structured logging.
 *
 * Extracts the `Request-ID` header from incoming requests (or generates
 * one if absent), places it in the SLF4J MDC so it appears in all log
 * lines for the request, echoes it back in the response header, and
 * logs each incoming request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestFilter : OncePerRequestFilter() {

    companion object {
        const val REQUEST_ID_KEY = "requestId"
        const val REQUEST_ID_HEADER = "Request-ID"
        private val UUID_PATTERN = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
    }

    /**
     * Establishes tracing context for each request, extracting or
     * generating a request ID and logging the incoming request.
     */
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientRequestId = request.getHeader(REQUEST_ID_HEADER)
        val requestId = if (clientRequestId != null && UUID_PATTERN.matcher(clientRequestId).matches()) {
            clientRequestId
        } else {
            UUID.randomUUID().toString()
        }

        val params = request.parameterMap.takeIf { it.isNotEmpty() }
            ?.mapValues { it.value.joinToString(", ") }
            ?: emptyMap<String, String>()

        MDC.put(REQUEST_ID_KEY, requestId)
        response.addHeader(REQUEST_ID_HEADER, requestId)
        request.setAttribute(REQUEST_ID_KEY, requestId)

        if (!request.requestURI.startsWith("/actuator")) {
            log.info { "Incoming request: ${request.method} ${request.requestURI} — params: $params" }
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID_KEY)
        }
    }
}
