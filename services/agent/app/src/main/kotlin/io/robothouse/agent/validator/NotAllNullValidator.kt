package io.robothouse.agent.validator

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Bean Validation constraint that ensures at least one property of
 * the annotated class is non-null. Used for PATCH request DTOs where
 * an empty update body should be rejected.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotAllNullValidator::class])
annotation class NotAllNull(
    val message: String = "At least one field must be provided",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for the [NotAllNull] constraint.
 *
 * Uses Kotlin reflection to check that at least one declared property
 * of the target object has a non-null value.
 */
class NotAllNullValidator : ConstraintValidator<NotAllNull, Any> {

    override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return value::class.memberProperties.any { prop ->
            prop.getter.call(value) != null
        }
    }
}
