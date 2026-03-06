package io.robothouse.agent.validator

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Bean Validation constraint that limits a string field to a maximum
 * number of tokens using the cl100k_base tokenizer encoding.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxTokensValidator::class])
annotation class MaxTokens(
    val value: Int,
    val message: String = "must not exceed {value} tokens",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for the [MaxTokens] constraint.
 *
 * Uses jtokkit's cl100k_base encoding to count tokens and validates
 * that the token count does not exceed the configured maximum.
 */
class MaxTokensValidator : ConstraintValidator<MaxTokens, String> {

    private var maxTokens: Int = 0

    private val encoding by lazy {
        Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE)
    }

    override fun initialize(annotation: MaxTokens) {
        maxTokens = annotation.value
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return encoding.countTokens(value) <= maxTokens
    }
}
