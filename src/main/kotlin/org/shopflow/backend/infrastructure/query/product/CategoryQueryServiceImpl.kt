package org.shopflow.backend.infrastructure.query.product

import jakarta.persistence.criteria.Predicate
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.CategorySortField
import org.shopflow.backend.application.query.product.ListAdminCategoriesQuery
import org.shopflow.backend.application.query.product.ListCategoriesQuery
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.infrastructure.persistence.entity.product.CategoryJpaEntity
import org.shopflow.backend.infrastructure.persistence.jpa.product.CategoryJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CategoryQueryServiceImpl(
    private val categoryJpaRepository: CategoryJpaRepository,
) : CategoryQueryService {
    override fun listPublicCategories(query: ListCategoriesQuery): PageResponseDto<CategoryDto> =
        executeCategoryPageQuery(
            page = query.page,
            size = query.size,
            isActiveFilter = true,
            parentId = query.parentId,
            keyword = query.keyword,
            sortField = query.sortField,
            sortDirection = query.sortDirection,
        )

    override fun listAdminCategories(query: ListAdminCategoriesQuery): PageResponseDto<CategoryDto> =
        executeCategoryPageQuery(
            page = query.page,
            size = query.size,
            isActiveFilter = query.isActive,
            parentId = query.parentId,
            keyword = query.keyword,
            sortField = query.sortField,
            sortDirection = query.sortDirection,
        )

    private fun executeCategoryPageQuery(
        page: Int,
        size: Int,
        isActiveFilter: Boolean?,
        parentId: String?,
        keyword: String?,
        sortField: CategorySortField,
        sortDirection: SortDirection,
    ): PageResponseDto<CategoryDto> {
        val specification = buildSpecification(isActiveFilter, parentId, keyword)
        val pageable = PageRequest.of(page, size, resolveSort(sortField, sortDirection))
        val pageResult = categoryJpaRepository.findAll(specification, pageable)

        val items =
            pageResult.content.map { entity ->
                CategoryDto(
                    id = entity.getId(),
                    name = entity.name,
                    slug = entity.slug,
                    parentId = entity.parentId,
                    description = entity.description,
                    displayOrder = entity.displayOrder,
                    isActive = entity.isActive,
                    createdAt = entity.createdAt ?: Instant.EPOCH,
                )
            }

        return PageResponseDto(
            items = items,
            page = pageResult.number,
            size = pageResult.size,
            totalElements = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            hasNext = pageResult.hasNext(),
            hasPrevious = pageResult.hasPrevious() && pageResult.totalPages > 0,
        )
    }

    private fun buildSpecification(
        isActiveFilter: Boolean?,
        parentId: String?,
        keyword: String?,
    ): Specification<CategoryJpaEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            when (isActiveFilter) {
                true -> predicates += cb.isTrue(root.get("isActive"))
                false -> predicates += cb.isFalse(root.get("isActive"))
                null -> Unit
            }
            if (parentId != null) {
                predicates += cb.equal(root.get<String>("parentId"), parentId)
            }
            if (keyword != null) {
                val pattern = "%${keyword.lowercase()}%"
                predicates +=
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("slug")), pattern),
                    )
            }
            cb.and(*predicates.toTypedArray())
        }

    private fun resolveSort(
        sortField: CategorySortField,
        sortDirection: SortDirection,
    ): Sort {
        val entityColumn = CATEGORY_SORT_COLUMN_WHITELIST.getValue(sortField)
        val springDirection =
            if (sortDirection == SortDirection.ASC) {
                Sort.Direction.ASC
            } else {
                Sort.Direction.DESC
            }
        return Sort.by(Sort.Order(springDirection, entityColumn), Sort.Order.asc("slug"))
    }

    companion object {
        private val CATEGORY_SORT_COLUMN_WHITELIST: Map<CategorySortField, String> =
            mapOf(
                CategorySortField.DISPLAY_ORDER to "displayOrder",
                CategorySortField.NAME to "name",
                CategorySortField.CREATED_AT to "createdAt",
            )
    }
}
