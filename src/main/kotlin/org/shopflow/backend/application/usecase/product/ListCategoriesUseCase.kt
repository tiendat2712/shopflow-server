package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.ListCategoriesQuery
import org.shopflow.backend.application.query.product.MAX_CATALOG_PAGE_SIZE
import org.shopflow.backend.application.query.product.resolveCategorySort
import org.shopflow.backend.application.query.product.validateOptionalCategoryUuid
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCategoriesUseCase(
    private val categoryQueryService: CategoryQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: ListCategoriesQuery = ListCategoriesQuery()): PageResponseDto<CategoryDto> {
        val (resolvedField, resolvedDirection) = resolveCategorySort(query.sortBy, query.sortDir)

        val sanitizedQuery =
            query.copy(
                page = query.page.coerceAtLeast(0),
                size = query.size.coerceIn(1, MAX_PAGE_SIZE),
                parentId = validateOptionalCategoryUuid(query.parentId, "parentId"),
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                sortBy = query.sortBy.trim(),
                sortDir = resolvedDirection.name.lowercase(),
                sortField = resolvedField,
                sortDirection = resolvedDirection,
            )

        return categoryQueryService.listPublicCategories(sanitizedQuery)
    }

    companion object {
        const val MAX_PAGE_SIZE = MAX_CATALOG_PAGE_SIZE
    }
}
