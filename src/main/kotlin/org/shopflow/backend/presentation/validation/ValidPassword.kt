package org.shopflow.backend.presentation.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PasswordConstraintValidator::class])
annotation class ValidPassword(
    val message: String =
        "Password must be at least 9 characters long and contain at least one digit, uppercase and lowercase character.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PasswordConstraintValidator : ConstraintValidator<ValidPassword, String?> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) {
            return false
        }
        return PASSWORD_REGEX.matches(value)
    }

    private companion object {
        val PASSWORD_REGEX = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{9,}\$")
    }
}
