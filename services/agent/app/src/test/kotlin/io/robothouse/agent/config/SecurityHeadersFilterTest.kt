package io.robothouse.agent.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SecurityHeadersFilterTest {

    private val filter = SecurityHeadersFilter()

    @Test
    fun `sets security headers on API responses`() {
        val request = MockHttpServletRequest("GET", "/api/skills")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("0", response.getHeader("X-XSS-Protection"))
        assertEquals("no-store", response.getHeader("Cache-Control"))
    }

    @Test
    fun `does not set Cache-Control no-store on non-API paths`() {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("0", response.getHeader("X-XSS-Protection"))
        assertNull(response.getHeader("Cache-Control"))
    }
}
