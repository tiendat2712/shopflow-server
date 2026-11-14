package org.shopflow.backend.infrastructure.query.product

import jakarta.persistence.criteria.Predicate
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.CategorySortField
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
    override fun listCategories(query: ListCategoriesQuery): PageResponseDto<CategoryDto> {
        val specification = buildSpecification(query)
        val pageable = PageRequest.of(query.page, query.size, resolveSort(query.sortField, query.sortDirection))
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

    private fun buildSpecification(query: ListCategoriesQuery): Specification<CategoryJpaEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (query.onlyActive) {
                predicates += cb.isTrue(root.get("isActive"))
            }
            if (query.parentId != null) {
                predicates += cb.equal(root.get<String>("parentId"), query.parentId)
            }
            if (query.keyword != null) {
                val pattern = "%${query.keyword.lowercase()}%"
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
