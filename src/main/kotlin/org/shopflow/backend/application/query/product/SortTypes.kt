package org.shopflow.backend.application.query.product

import org.shopflow.backend.domain.exception.DomainValidationException

enum class SortDirection {
    ASC,
    DESC,
}

enum class ProductSortField {
    CREATED_AT,
    PRICE,
    NAME,
    AVERAGE_RATING,
}

enum class CategorySortField {
    DISPLAY_ORDER,
    NAME,
    CREATED_AT,
}

internal fun <T : Enum<T>> resolveSortSpec(
    rawSortBy: String,
    rawSortDir: String,
    presets: Map<String, Pair<T, SortDirection>>,
    fields: Map<String, T>,
    allowedFieldMessage: String,
): Pair<T, SortDirection> {
    val trimmedBy = rawSortBy.trim()
    val (candidateField, candidateDir) =
        if (trimmedBy.contains(",")) {
            val parts = trimmedBy.split(",", limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            trimmedBy to rawSortDir.trim()
        }

    val normalizedField = candidateField.lowercase()
    presets[normalizedField]?.let { return it }

    val resolvedField =
        fields[normalizedField]
            ?: throw DomainValidationException("Invalid sortBy '$rawSortBy'. Allowed: $allowedFieldMessage.")

    val resolvedDirection =
        when (candidateDir.lowercase()) {
            "asc" -> SortDirection.ASC
            "desc" -> SortDirection.DESC
            else -> throw DomainValidationException("Invalid sortDir '$candidateDir'. Allowed: asc, desc.")
        }

    return resolvedField to resolvedDirection
}
