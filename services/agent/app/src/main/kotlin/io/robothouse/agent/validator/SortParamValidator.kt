package io.robothouse.agent.validator

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Bean Validation constraint that verifies a sort parameter string
 * contains a valid property name (derived from the target entity class)
 * and an optional valid direction (`asc` or `desc`).
 *
 * Expected format: `"property,direction"` (e.g. `"createdAt,desc"`).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SortParamValidator::class])
annotation class ValidSortParam(
    val entity: KClass<*>,
    val message: String = "Invalid sort parameter",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for the [ValidSortParam] constraint.
 *
 * Derives allowed property names from the entity class via Kotlin reflection
 * and validates the direction component against `asc`/`desc`.
 */
class SortParamValidator : ConstraintValidator<ValidSortParam, String> {

    private lateinit var allowedProperties: Set<String>

    override fun initialize(constraintAnnotation: ValidSortParam) {
        allowedProperties = propertyCache.getOrPut(constraintAnnotation.entity) {
            constraintAnnotation.entity.memberProperties.map { it.name }.toSet()
        }
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true

        val parts = value.split(",", limit = 2)
        val property = parts[0]
        val direction = if (parts.size > 1) parts[1].lowercase() else null

        val errors = mutableListOf<String>()

        if (property !in allowedProperties) {
            errors.add("Unknown sort property: '$property'. Allowed: ${allowedProperties.sorted().joinToString(", ")}")
        }

        if (direction != null && direction !in ALLOWED_DIRECTIONS) {
            errors.add("Invalid sort direction: '$direction'. Allowed: asc, desc")
        }

        if (errors.isEmpty()) return true

        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(errors.joinToString("; "))
            .addConstraintViolation()
        return false
    }

    companion object {
        private val ALLOWED_DIRECTIONS = setOf("asc", "desc")
        private val propertyCache = ConcurrentHashMap<KClass<*>, Set<String>>()
    }
}
