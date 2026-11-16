package org.shopflow.backend.application.query.product

import org.shopflow.backend.domain.exception.DomainValidationException

const val MAX_CATALOG_PAGE_SIZE = 100

private val PRODUCT_SORT_PRESETS =
    mapOf(
        "newest" to (ProductSortField.CREATED_AT to SortDirection.DESC),
        "oldest" to (ProductSortField.CREATED_AT to SortDirection.ASC),
        "price_asc" to (ProductSortField.PRICE to SortDirection.ASC),
        "price_desc" to (ProductSortField.PRICE to SortDirection.DESC),
        "name_asc" to (ProductSortField.NAME to SortDirection.ASC),
        "name_desc" to (ProductSortField.NAME to SortDirection.DESC),
        "rating_desc" to (ProductSortField.AVERAGE_RATING to SortDirection.DESC),
        "rating_asc" to (ProductSortField.AVERAGE_RATING to SortDirection.ASC),
    )

private val ALLOWED_PRODUCT_SORT_FIELDS =
    mapOf(
        "createdat" to ProductSortField.CREATED_AT,
        "created_at" to ProductSortField.CREATED_AT,
        "price" to ProductSortField.PRICE,
        "name" to ProductSortField.NAME,
        "averagerating" to ProductSortField.AVERAGE_RATING,
        "average_rating" to ProductSortField.AVERAGE_RATING,
        "rating" to ProductSortField.AVERAGE_RATING,
    )

private val CATEGORY_SORT_PRESETS =
    mapOf(
        "newest" to (CategorySortField.CREATED_AT to SortDirection.DESC),
        "oldest" to (CategorySortField.CREATED_AT to SortDirection.ASC),
        "name_asc" to (CategorySortField.NAME to SortDirection.ASC),
        "name_desc" to (CategorySortField.NAME to SortDirection.DESC),
    )

private val ALLOWED_CATEGORY_SORT_FIELDS =
    mapOf(
        "displayorder" to CategorySortField.DISPLAY_ORDER,
        "display_order" to CategorySortField.DISPLAY_ORDER,
        "name" to CategorySortField.NAME,
        "createdat" to CategorySortField.CREATED_AT,
        "created_at" to CategorySortField.CREATED_AT,
    )

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

internal fun resolveProductSort(
    rawSortBy: String,
    rawSortDir: String,
): Pair<ProductSortField, SortDirection> =
    resolveSortSpec(
        rawSortBy = rawSortBy,
        rawSortDir = rawSortDir,
        presets = PRODUCT_SORT_PRESETS,
        fields = ALLOWED_PRODUCT_SORT_FIELDS,
        allowedFieldMessage = "createdAt, price, name, averageRating",
    )

internal fun resolveCategorySort(
    rawSortBy: String,
    rawSortDir: String,
): Pair<CategorySortField, SortDirection> =
    resolveSortSpec(
        rawSortBy = rawSortBy,
        rawSortDir = rawSortDir,
        presets = CATEGORY_SORT_PRESETS,
        fields = ALLOWED_CATEGORY_SORT_FIELDS,
        allowedFieldMessage = "displayOrder, name, createdAt",
    )

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
