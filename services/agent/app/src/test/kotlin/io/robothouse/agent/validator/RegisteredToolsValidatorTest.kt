package io.robothouse.agent.validator

import io.robothouse.agent.repository.ToolRepository
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

class RegisteredToolsValidatorTest {

    private val toolRepository: ToolRepository = mock()
    private val context: ConstraintValidatorContext = mock()
    private val violationBuilder: ConstraintValidatorContext.ConstraintViolationBuilder = mock()
    private lateinit var validator: RegisteredToolsValidator

    @BeforeEach
    fun setUp() {
        validator = RegisteredToolsValidator(toolRepository)
        whenever(toolRepository.getToolNames()).thenReturn(setOf("DateTimeTool", "WebSearchTool", "CalculatorTool"))
        whenever(context.buildConstraintViolationWithTemplate(any())).thenReturn(violationBuilder)
    }

    @Test
    fun `null value is valid`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun `empty list is valid when rejectEmpty is false`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertTrue(validator.isValid(emptyList(), context))
    }

    @Test
    fun `empty list is invalid when rejectEmpty is true`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(true)
        validator.initialize(annotation)

        assertFalse(validator.isValid(emptyList(), context))
        verify(context).disableDefaultConstraintViolation()
        verify(context).buildConstraintViolationWithTemplate("Tool names must not be empty")
    }

    @Test
    fun `all registered tools is valid`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertTrue(validator.isValid(listOf("DateTimeTool", "WebSearchTool"), context))
    }

    @Test
    fun `unknown tool names are invalid`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertFalse(validator.isValid(listOf("DateTimeTool", "FakeTool"), context))
        verify(context).disableDefaultConstraintViolation()
        verify(context).buildConstraintViolationWithTemplate("Unknown tool name(s): FakeTool")
    }

    @Test
    fun `multiple unknown tools are listed in error message`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertFalse(validator.isValid(listOf("FakeTool", "AnotherFake"), context))
        verify(context).buildConstraintViolationWithTemplate("Unknown tool name(s): FakeTool, AnotherFake")
    }

    @Test
    fun `single registered tool is valid`() {
        val annotation: RegisteredTools = mock()
        whenever(annotation.rejectEmpty).thenReturn(false)
        validator.initialize(annotation)

        assertTrue(validator.isValid(listOf("CalculatorTool"), context))
        verify(context, never()).disableDefaultConstraintViolation()
    }
}
