package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.query.product.ListAdminProductsQuery
import org.shopflow.backend.application.query.product.MAX_CATALOG_PAGE_SIZE
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.application.query.product.resolveProductSort
import org.shopflow.backend.application.query.product.validateCategoryFilterSelection
import org.shopflow.backend.application.query.product.validateOptionalCategoryUuid
import org.shopflow.backend.application.query.product.validateOptionalMinRating
import org.shopflow.backend.application.query.product.validatePriceRange
import org.shopflow.backend.domain.model.product.ProductStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListAdminProductsUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: ListAdminProductsQuery): PageResponseDto<ProductSummaryDto> {
        val (normalizedMinPrice, normalizedMaxPrice) = validatePriceRange(query.minPrice, query.maxPrice)
        val normalizedMinRating = validateOptionalMinRating(query.minRating)
        val normalizedCategoryId = validateOptionalCategoryUuid(query.categoryId, "categoryId")
        val normalizedCategorySlug =
            query.categorySlug
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        validateCategoryFilterSelection(normalizedCategoryId, normalizedCategorySlug)

        val (resolvedField, resolvedDirection) = resolveProductSort(query.sortBy, query.sortDir)

        val normalizedStatus =
            query.status
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }
        val resolvedTargetStatus =
            when (normalizedStatus) {
                null, "ALL" -> null
                else -> ProductStatus.fromString(normalizedStatus)
            }

        val sanitizedQuery =
            query.copy(
                page = query.page.coerceAtLeast(0),
                size = query.size.coerceIn(1, MAX_CATALOG_PAGE_SIZE),
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                categoryId = normalizedCategoryId,
                categorySlug = normalizedCategorySlug,
                minPrice = normalizedMinPrice,
                maxPrice = normalizedMaxPrice,
                minRating = normalizedMinRating,
                status = normalizedStatus,
                targetStatus = resolvedTargetStatus,
                sortBy = query.sortBy.trim(),
                sortDir = resolvedDirection.name.lowercase(),
                sortField = resolvedField,
                sortDirection = resolvedDirection,
            )

        return productQueryService.listAdminProducts(sanitizedQuery)
    }
}
