package org.shopflow.backend.application.query.product

import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.RatingAverage
import java.math.BigDecimal
import java.math.RoundingMode

internal fun validateOptionalPriceFilter(
    value: BigDecimal?,
    fieldName: String,
): BigDecimal? {
    if (value == null) {
        return null
    }
    if (value < Money.MIN_AMOUNT || value > Money.MAX_AMOUNT) {
        throw DomainValidationException("$fieldName must be between 0.00 and 9999999999999.99.")
    }
    return try {
        value.setScale(Money.SCALE, RoundingMode.UNNECESSARY)
    } catch (_: ArithmeticException) {
        throw DomainValidationException("$fieldName must have at most ${Money.SCALE} decimal places.")
    }
}

internal fun validatePriceRange(
    minPrice: BigDecimal?,
    maxPrice: BigDecimal?,
): Pair<BigDecimal?, BigDecimal?> {
    val normalizedMin = validateOptionalPriceFilter(minPrice, "minPrice")
    val normalizedMax = validateOptionalPriceFilter(maxPrice, "maxPrice")
    if (normalizedMin != null && normalizedMax != null && normalizedMin > normalizedMax) {
        throw DomainValidationException("minPrice cannot exceed maxPrice.")
    }
    return normalizedMin to normalizedMax
}

internal fun validateOptionalMinRating(minRating: BigDecimal?): BigDecimal? {
    if (minRating == null) {
        return null
    }
    if (minRating < BigDecimal.ZERO || minRating > RatingAverage.MAX_RATING) {
        throw DomainValidationException("minRating must be between 0 and 5.")
    }
    return minRating
}

internal fun validateOptionalCategoryUuid(
    raw: String?,
    fieldName: String = "categoryId",
): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        CategoryId.fromString(trimmed).value.toString()
    } catch (_: DomainValidationException) {
        val message =
            if (fieldName == "categoryId") {
                "Invalid category ID format."
            } else {
                "Invalid $fieldName format."
            }
        throw DomainValidationException(message)
    }
}

internal fun validateCategoryFilterSelection(
    categoryId: String?,
    categorySlug: String?,
) {
    if (categoryId != null && categorySlug != null) {
        throw DomainValidationException("Cannot specify both categoryId and categorySlug; provide only one.")
    }
}
