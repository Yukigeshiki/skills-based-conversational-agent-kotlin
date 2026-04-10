package io.robothouse.agent.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Adds defensive security headers to every HTTP response.
 *
 * Runs after [RequestFilter] so the request ID is already in the MDC.
 * Headers that depend on a reverse proxy (Strict-Transport-Security,
 * Content-Security-Policy) are intentionally omitted — the proxy owns
 * those concerns.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SecurityHeadersFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("X-XSS-Protection", "0")

        if (request.requestURI.startsWith("/api")) {
            response.setHeader("Cache-Control", "no-store")
        }

        filterChain.doFilter(request, response)
    }
}
