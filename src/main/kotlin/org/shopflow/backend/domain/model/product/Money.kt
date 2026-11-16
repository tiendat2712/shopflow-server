package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money(
    val amount: BigDecimal,
) {
    init {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw DomainValidationException("Money amount must be between 0.00 and 9999999999999.99.")
        }
        if (amount.scale() != SCALE) {
            throw DomainValidationException("Money amount must have scale $SCALE.")
        }
    }

    companion object {
        const val SCALE = 2
        val MIN_AMOUNT: BigDecimal = BigDecimal("0.00")
        val MAX_AMOUNT: BigDecimal = BigDecimal("9999999999999.99")
        val ZERO = Money(MIN_AMOUNT)

        fun of(raw: BigDecimal): Money {
            // Check range before scaling so oversized scientific-notation exponents fail fast.
            if (raw < MIN_AMOUNT || raw > MAX_AMOUNT) {
                throw DomainValidationException("Money amount must be between 0.00 and 9999999999999.99.")
            }
            val normalized =
                try {
                    raw.setScale(SCALE, RoundingMode.UNNECESSARY)
                } catch (_: ArithmeticException) {
                    throw DomainValidationException("Money amount must have at most $SCALE decimal places.")
                }
            return Money(normalized)
        }

        fun of(raw: String): Money =
            try {
                of(BigDecimal(raw.trim()))
            } catch (_: NumberFormatException) {
                throw DomainValidationException("Invalid monetary amount: '$raw'.")
            }
    }
}
