package org.shopflow.backend.application.query.product

import java.math.BigDecimal

data class ListProductsQuery(
    val page: Int = 0,
    val size: Int = 20,
    val categoryId: String? = null,
    val categorySlug: String? = null,
    val keyword: String? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val minRating: BigDecimal? = null,
    val status: String? = null,
    val sortBy: String = "createdAt",
    val sortDir: String = "desc",
    val sortField: ProductSortField = ProductSortField.CREATED_AT,
    val sortDirection: SortDirection = SortDirection.DESC,
)
