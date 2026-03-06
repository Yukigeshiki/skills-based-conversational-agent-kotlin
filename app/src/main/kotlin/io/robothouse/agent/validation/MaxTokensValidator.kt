package io.robothouse.agent.validation

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MaxTokensValidator::class])
annotation class MaxTokens(
    val value: Int,
    val message: String = "must not exceed {value} tokens",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

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
