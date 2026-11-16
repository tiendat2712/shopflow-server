package org.shopflow.backend.presentation.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.shopflow.backend.domain.model.product.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PriceConstraintValidator::class])
annotation class ValidPrice(
    val message: String = "Product price must be between 0.00 and 9999999999999.99 with at most 2 decimal places.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PriceConstraintValidator : ConstraintValidator<ValidPrice, BigDecimal?> {
    override fun isValid(
        value: BigDecimal?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) {
            return true
        }
        if (value < Money.MIN_AMOUNT || value > Money.MAX_AMOUNT) {
            setCustomMessage(context, "Product price must be between 0.00 and 9999999999999.99.")
            return false
        }
        return try {
            value.setScale(Money.SCALE, RoundingMode.UNNECESSARY)
            true
        } catch (_: ArithmeticException) {
            setCustomMessage(context, "Product price must have at most 2 decimal places.")
            false
        }
    }

    private fun setCustomMessage(
        context: ConstraintValidatorContext,
        message: String,
    ) {
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation()
    }
}
