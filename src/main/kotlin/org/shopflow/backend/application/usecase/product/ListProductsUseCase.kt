package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.application.query.product.ProductSortField
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.application.query.product.resolveSortSpec
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.model.product.ProductStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: ListProductsQuery): PageResponseDto<ProductSummaryDto> {
        if (query.minPrice != null && query.maxPrice != null && query.minPrice > query.maxPrice) {
            throw DomainValidationException("minPrice cannot exceed maxPrice.")
        }

        val (resolvedField, resolvedDirection) =
            resolveSortSpec(
                rawSortBy = query.sortBy,
                rawSortDir = query.sortDir,
                presets = SORT_PRESETS,
                fields = ALLOWED_SORT_FIELDS,
                allowedFieldMessage = "createdAt, price, name, averageRating",
            )

        val normalizedStatus =
            query.status
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }
        if (normalizedStatus != null && normalizedStatus != "ALL") {
            ProductStatus.fromString(normalizedStatus)
        }

        val sanitizedQuery =
            query.copy(
                page = query.page.coerceAtLeast(0),
                size = query.size.coerceIn(1, MAX_PAGE_SIZE),
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                categoryId = query.categoryId?.trim()?.takeIf { it.isNotEmpty() },
                categorySlug =
                    query.categorySlug
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() },
                status = normalizedStatus,
                sortBy = query.sortBy.trim(),
                sortDir = resolvedDirection.name.lowercase(),
                sortField = resolvedField,
                sortDirection = resolvedDirection,
            )

        return productQueryService.listProducts(sanitizedQuery)
    }

    companion object {
        const val MAX_PAGE_SIZE = 100

        private val SORT_PRESETS =
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

        private val ALLOWED_SORT_FIELDS =
            mapOf(
                "createdat" to ProductSortField.CREATED_AT,
                "created_at" to ProductSortField.CREATED_AT,
                "price" to ProductSortField.PRICE,
                "name" to ProductSortField.NAME,
                "averagerating" to ProductSortField.AVERAGE_RATING,
                "average_rating" to ProductSortField.AVERAGE_RATING,
                "rating" to ProductSortField.AVERAGE_RATING,
            )
    }
}
