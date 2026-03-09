package io.robothouse.agent.validator

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MaxTokensValidatorTest {

    private val context: ConstraintValidatorContext = mock()
    private lateinit var validator: MaxTokensValidator

    @BeforeEach
    fun setUp() {
        validator = MaxTokensValidator()
        val annotation: MaxTokens = mock()
        whenever(annotation.value).thenReturn(10)
        validator.initialize(annotation)
    }

    @Test
    fun `null value is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `empty string is valid`() {
        assertTrue(validator.isValid("", context))
    }

    @Test
    fun `short string within token limit is valid`() {
        assertTrue(validator.isValid("Hello world", context))
    }

    @Test
    fun `string exceeding token limit is invalid`() {
        // A long string that will exceed 10 tokens
        val longText = "This is a very long string that contains many words and should definitely exceed the ten token limit we set"
        assertFalse(validator.isValid(longText, context))
    }

    @Test
    fun `string at exactly the token limit is valid`() {
        // "one two three four five six seven eight nine ten" = 10 tokens
        val exactText = "one two three four five six seven eight nine ten"
        assertTrue(validator.isValid(exactText, context))
    }

    @Test
    fun `single token string is valid`() {
        assertTrue(validator.isValid("hello", context))
    }

    @Test
    fun `respects initialized max value`() {
        val strictValidator = MaxTokensValidator()
        val annotation: MaxTokens = mock()
        whenever(annotation.value).thenReturn(1)
        strictValidator.initialize(annotation)

        assertTrue(strictValidator.isValid("hello", context))
        assertFalse(strictValidator.isValid("hello world", context))
    }
}
