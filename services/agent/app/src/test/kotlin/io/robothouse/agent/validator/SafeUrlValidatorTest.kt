package io.robothouse.agent.validator

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SafeUrlValidatorTest {

    private val context: ConstraintValidatorContext = mock()
    private lateinit var validator: SafeUrlValidator

    @BeforeEach
    fun setUp() {
        validator = SafeUrlValidator()
    }

    @Test
    fun `valid HTTPS URL passes`() {
        assertTrue(SafeUrlValidator.isSafeUrl("https://example.com/data"))
    }

    @Test
    fun `valid HTTP URL passes`() {
        assertTrue(SafeUrlValidator.isSafeUrl("http://example.com/data"))
    }

    @Test
    fun `null value is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `blank value is valid`() {
        assertTrue(validator.isValid("", context))
    }

    @Test
    fun `FTP scheme is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("ftp://files.example.com"))
    }

    @Test
    fun `file scheme is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("file:///etc/passwd"))
    }

    @Test
    fun `malformed URL is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("not-a-url"))
    }

    @Test
    fun `localhost is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("http://localhost:8080"))
    }

    @Test
    fun `metadata endpoint is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("http://169.254.169.254/latest/meta-data/"))
    }

    @Test
    fun `metadata google internal is rejected`() {
        assertFalse(SafeUrlValidator.isSafeUrl("http://metadata.google.internal/computeMetadata/"))
    }
}
