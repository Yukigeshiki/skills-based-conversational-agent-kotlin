package io.robothouse.agent.validator

import io.robothouse.agent.entity.Skill
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SortParamValidatorTest {

    private val context: ConstraintValidatorContext = mock()
    private val violationBuilder: ConstraintValidatorContext.ConstraintViolationBuilder = mock()
    private lateinit var validator: SortParamValidator

    private fun annotatedMethod(@ValidSortParam(entity = Skill::class) sort: String?) {}

    @BeforeEach
    fun setUp() {
        validator = SortParamValidator()
        val annotation = this::class.java.getDeclaredMethod("annotatedMethod", String::class.java)
            .parameters[0]
            .getAnnotation(ValidSortParam::class.java)
        validator.initialize(annotation)

        whenever(context.buildConstraintViolationWithTemplate(any())).thenReturn(violationBuilder)
    }

    @Test
    fun `null value is valid`() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `valid property name without direction is valid`() {
        assertTrue(validator.isValid("name", context))
    }

    @Test
    fun `valid property name with asc direction is valid`() {
        assertTrue(validator.isValid("name,asc", context))
    }

    @Test
    fun `valid property name with desc direction is valid`() {
        assertTrue(validator.isValid("createdAt,desc", context))
    }

    @Test
    fun `direction is case-insensitive`() {
        assertTrue(validator.isValid("name,ASC", context))
        assertTrue(validator.isValid("name,Desc", context))
    }

    @Test
    fun `unknown property name is invalid`() {
        assertFalse(validator.isValid("nonExistentField", context))
        verify(context).disableDefaultConstraintViolation()
        verify(context).buildConstraintViolationWithTemplate(any())
    }

    @Test
    fun `invalid direction is invalid`() {
        assertFalse(validator.isValid("name,sideways", context))
        verify(context).disableDefaultConstraintViolation()
    }

    @Test
    fun `unknown property and invalid direction reports both errors`() {
        assertFalse(validator.isValid("fakeField,sideways", context))
        verify(context).disableDefaultConstraintViolation()
    }

    @Test
    fun `valid Skill properties are accepted`() {
        assertTrue(validator.isValid("name", context))
        assertTrue(validator.isValid("description", context))
        assertTrue(validator.isValid("systemPrompt", context))
        assertTrue(validator.isValid("createdAt", context))
        assertTrue(validator.isValid("updatedAt", context))
    }

    @Test
    fun `does not modify context on valid input`() {
        validator.isValid("name,asc", context)

        verify(context, never()).disableDefaultConstraintViolation()
        verify(context, never()).buildConstraintViolationWithTemplate(any())
    }
}
