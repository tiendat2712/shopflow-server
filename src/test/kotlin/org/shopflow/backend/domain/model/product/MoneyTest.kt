package org.shopflow.backend.domain.model.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.exception.DomainValidationException
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `accepts boundary values, lower decimal precision, scientific notation, and lossless trailing zeros`() {
        assertEquals(BigDecimal("0.00"), Money.of(BigDecimal("0")).amount)
        assertEquals(BigDecimal("0.00"), Money.of(BigDecimal("0.00")).amount)
        assertEquals(BigDecimal("0.00"), Money.of(BigDecimal("0.000")).amount)
        assertEquals(BigDecimal("0.00"), Money.of(BigDecimal("-0.00")).amount)
        assertEquals(BigDecimal("19.90"), Money.of(BigDecimal("19.9")).amount)
        assertEquals(BigDecimal("19.99"), Money.of(BigDecimal("19.9900")).amount)
        assertEquals(BigDecimal("100.00"), Money.of(BigDecimal("1E+2")).amount)
        assertEquals(BigDecimal("9999999999999.99"), Money.of(BigDecimal("9999999999999.99")).amount)
        assertEquals(BigDecimal("9999999999999.99"), Money.of(BigDecimal("9999999999999.9900")).amount)
        assertEquals(BigDecimal("49.90"), Money.of(" 49.900 ").amount)
    }

    @Test
    fun `rejects out of range values including negative and exceeding DECIMAL(15, 2) max`() {
        val outOfRangeValues =
            listOf(
                BigDecimal("-0.01"),
                BigDecimal("-1"),
                BigDecimal("9999999999999.991"),
                BigDecimal("10000000000000.00"),
                BigDecimal("1E+20"),
            )
        for (value in outOfRangeValues) {
            assertThrows(DomainValidationException::class.java) {
                Money.of(value)
            }
        }

        assertThrows(DomainValidationException::class.java) {
            Money(BigDecimal("-0.01"))
        }
        assertThrows(DomainValidationException::class.java) {
            Money(BigDecimal("10000000000000.00"))
        }
        assertThrows(DomainValidationException::class.java) {
            Money(BigDecimal("10.0"))
        }
    }

    @Test
    fun `rejects non-zero fractional digits beyond two decimal places instead of rounding`() {
        val excessivePrecisionValues =
            listOf(
                BigDecimal("29.995"),
                BigDecimal("0.001"),
                BigDecimal("10.129"),
                BigDecimal("9999999999999.985"),
            )
        for (value in excessivePrecisionValues) {
            assertThrows(DomainValidationException::class.java) {
                Money.of(value)
            }
        }

        assertThrows(DomainValidationException::class.java) {
            Money.of("19.999")
        }
        assertThrows(DomainValidationException::class.java) {
            Money.of("not-a-number")
        }
    }
}
