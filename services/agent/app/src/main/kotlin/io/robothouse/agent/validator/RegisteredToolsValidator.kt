package io.robothouse.agent.validator

import io.robothouse.agent.repository.ToolRepository
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Bean Validation constraint that verifies all tool names in a list
 * are registered in the [ToolRepository].
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [RegisteredToolsValidator::class])
annotation class RegisteredTools(
    val rejectEmpty: Boolean = false,
    val message: String = "contains unknown tool name(s)",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for the [RegisteredTools] constraint.
 *
 * Checks each tool name against the set of registered tool beans
 * discovered by [ToolRepository].
 */
class RegisteredToolsValidator(
    private val toolRepository: ToolRepository
) : ConstraintValidator<RegisteredTools, List<String>> {

    private var rejectEmpty = false

    override fun initialize(constraintAnnotation: RegisteredTools) {
        rejectEmpty = constraintAnnotation.rejectEmpty
    }

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        if (value.isEmpty()) {
            if (!rejectEmpty) return true
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate("Tool names must not be empty")
                .addConstraintViolation()
            return false
        }
        val registeredTools = toolRepository.getToolNames()
        val unknownTools = value.filter { it !in registeredTools }
        if (unknownTools.isEmpty()) return true

        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(
            "Unknown tool name(s): ${unknownTools.joinToString(", ")}"
        ).addConstraintViolation()
        return false
    }
}
