package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class RatingAverage(
    val value: BigDecimal,
) {
    init {
        if (value < BigDecimal.ZERO || value > MAX_RATING) {
            throw DomainValidationException("Average rating must be between 0.00 and 5.00.")
        }
        if (value.scale() != SCALE) {
            throw DomainValidationException("Average rating must have scale $SCALE.")
        }
    }

    companion object {
        const val SCALE = 2
        val MAX_RATING = BigDecimal("5.00")
        val ZERO = RatingAverage(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))

        fun of(raw: BigDecimal): RatingAverage = RatingAverage(raw.setScale(SCALE, RoundingMode.HALF_UP))

        fun ofClamped(raw: String): RatingAverage =
            try {
                val parsed = BigDecimal(raw.trim()).setScale(SCALE, RoundingMode.HALF_UP)
                val clamped = parsed.coerceIn(BigDecimal.ZERO.setScale(SCALE), MAX_RATING)
                RatingAverage(clamped)
            } catch (ex: NumberFormatException) {
                ZERO
            }
    }
}
