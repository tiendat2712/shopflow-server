package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money(
    val amount: BigDecimal,
) {
    init {
        if (amount < BigDecimal.ZERO) {
            throw DomainValidationException("Money amount cannot be negative.")
        }
        if (amount.scale() != SCALE) {
            throw DomainValidationException("Money amount must have scale $SCALE.")
        }
    }

    companion object {
        const val SCALE = 2
        val ZERO = Money(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))

        fun of(raw: BigDecimal): Money = Money(raw.setScale(SCALE, RoundingMode.HALF_UP))

        fun of(raw: String): Money =
            try {
                of(BigDecimal(raw.trim()))
            } catch (ex: NumberFormatException) {
                throw DomainValidationException("Invalid monetary amount: '$raw'.")
            }
    }
}
