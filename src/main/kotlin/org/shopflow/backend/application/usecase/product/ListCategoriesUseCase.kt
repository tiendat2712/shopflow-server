package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.CategorySortField
import org.shopflow.backend.application.query.product.ListCategoriesQuery
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.application.query.product.resolveSortSpec
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCategoriesUseCase(
    private val categoryQueryService: CategoryQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: ListCategoriesQuery = ListCategoriesQuery()): PageResponseDto<CategoryDto> {
        val (resolvedField, resolvedDirection) =
            resolveSortSpec(
                rawSortBy = query.sortBy,
                rawSortDir = query.sortDir,
                presets = SORT_PRESETS,
                fields = ALLOWED_SORT_FIELDS,
                allowedFieldMessage = "displayOrder, name, createdAt",
            )

        val sanitizedQuery =
            query.copy(
                page = query.page.coerceAtLeast(0),
                size = query.size.coerceIn(1, MAX_PAGE_SIZE),
                parentId = query.parentId?.trim()?.takeIf { it.isNotEmpty() },
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                sortBy = query.sortBy.trim(),
                sortDir = resolvedDirection.name.lowercase(),
                sortField = resolvedField,
                sortDirection = resolvedDirection,
            )

        return categoryQueryService.listCategories(sanitizedQuery)
    }

    companion object {
        const val MAX_PAGE_SIZE = 100

        private val SORT_PRESETS =
            mapOf(
                "newest" to (CategorySortField.CREATED_AT to SortDirection.DESC),
                "oldest" to (CategorySortField.CREATED_AT to SortDirection.ASC),
                "name_asc" to (CategorySortField.NAME to SortDirection.ASC),
                "name_desc" to (CategorySortField.NAME to SortDirection.DESC),
            )

        private val ALLOWED_SORT_FIELDS =
            mapOf(
                "displayorder" to CategorySortField.DISPLAY_ORDER,
                "display_order" to CategorySortField.DISPLAY_ORDER,
                "name" to CategorySortField.NAME,
                "createdat" to CategorySortField.CREATED_AT,
                "created_at" to CategorySortField.CREATED_AT,
            )
    }
}
