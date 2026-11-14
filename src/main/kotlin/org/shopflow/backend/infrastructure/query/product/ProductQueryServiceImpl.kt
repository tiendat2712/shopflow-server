package org.shopflow.backend.infrastructure.query.product

import jakarta.persistence.criteria.Predicate
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.application.query.product.ProductSortField
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.infrastructure.persistence.entity.product.ProductJpaEntity
import org.shopflow.backend.infrastructure.persistence.jpa.product.CategoryJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.product.ProductJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ProductQueryServiceImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
) : ProductQueryService {
    override fun listProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto> {
        var resolvedCategoryId = query.categoryId
        if (resolvedCategoryId == null && query.categorySlug != null) {
            val category =
                categoryJpaRepository.findBySlug(query.categorySlug)
                    ?: return PageResponseDto(
                        items = emptyList(),
                        page = query.page,
                        size = query.size,
                        totalElements = 0L,
                        totalPages = 0,
                        hasNext = false,
                        hasPrevious = false,
                    )
            resolvedCategoryId = category.getId()
        }

        val targetStatus: ProductStatus? =
            when {
                query.status == null -> ProductStatus.ACTIVE
                query.status.equals("ALL", ignoreCase = true) -> null
                else -> ProductStatus.fromString(query.status)
            }

        val specification = buildSpecification(resolvedCategoryId, query, targetStatus)
        val pageable = PageRequest.of(query.page, query.size, resolveSort(query.sortField, query.sortDirection))
        val pageResult = productJpaRepository.findAll(specification, pageable)

        val categoryIds = pageResult.content.mapTo(hashSetOf()) { it.categoryId }
        val categoryNamesById =
            if (categoryIds.isEmpty()) {
                emptyMap()
            } else {
                categoryJpaRepository.findAllById(categoryIds).associate { it.getId() to it.name }
            }

        val items =
            pageResult.content.map { entity ->
                ProductSummaryDto(
                    id = entity.getId(),
                    categoryId = entity.categoryId,
                    categoryName = categoryNamesById[entity.categoryId] ?: "General",
                    name = entity.name,
                    slug = entity.slug,
                    sku = entity.sku,
                    price = entity.price,
                    stockQuantity = entity.stockQuantity,
                    averageRating = entity.averageRating,
                    reviewCount = entity.reviewCount,
                    imageUrl = entity.imageUrl,
                    status = entity.status.name,
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

    override fun findProductDetail(idOrSlug: String): ProductDetailDto? {
        val parsedUuid = runCatching { UUID.fromString(idOrSlug) }.getOrNull()
        val entity =
            if (parsedUuid != null) {
                productJpaRepository.findById(parsedUuid.toString()).orElse(null)
            } else {
                productJpaRepository.findBySlug(idOrSlug.lowercase())
            } ?: return null

        val category = categoryJpaRepository.findById(entity.categoryId).orElse(null)
        return ProductDetailDto(
            id = entity.getId(),
            categoryId = entity.categoryId,
            categoryName = category?.name ?: "General",
            categorySlug = category?.slug ?: "general",
            name = entity.name,
            slug = entity.slug,
            sku = entity.sku,
            description = entity.description,
            price = entity.price,
            stockQuantity = entity.stockQuantity,
            averageRating = entity.averageRating,
            reviewCount = entity.reviewCount,
            imageUrl = entity.imageUrl,
            status = entity.status.name,
            createdAt = entity.createdAt ?: Instant.EPOCH,
            updatedAt = entity.updatedAt ?: Instant.EPOCH,
        )
    }

    private fun buildSpecification(
        categoryId: String?,
        query: ListProductsQuery,
        status: ProductStatus?,
    ): Specification<ProductJpaEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            if (status != null) {
                predicates += cb.equal(root.get<ProductStatus>("status"), status)
            }

            if (categoryId != null) {
                predicates += cb.equal(root.get<String>("categoryId"), categoryId)
            }
            if (query.keyword != null) {
                val pattern = "%${query.keyword.lowercase()}%"
                predicates +=
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern),
                    )
            }
            if (query.minPrice != null) {
                predicates += cb.greaterThanOrEqualTo(root.get<BigDecimal>("price"), query.minPrice)
            }
            if (query.maxPrice != null) {
                predicates += cb.lessThanOrEqualTo(root.get<BigDecimal>("price"), query.maxPrice)
            }
            if (query.minRating != null) {
                predicates += cb.greaterThanOrEqualTo(root.get<BigDecimal>("averageRating"), query.minRating)
            }

            cb.and(*predicates.toTypedArray())
        }

    private fun resolveSort(
        sortField: ProductSortField,
        sortDirection: SortDirection,
    ): Sort {
        val entityColumn = PRODUCT_SORT_COLUMN_WHITELIST.getValue(sortField)
        val springDirection =
            if (sortDirection == SortDirection.ASC) {
                Sort.Direction.ASC
            } else {
                Sort.Direction.DESC
            }
        return Sort.by(Sort.Order(springDirection, entityColumn), Sort.Order.asc("slug"))
    }

    companion object {
        private val PRODUCT_SORT_COLUMN_WHITELIST: Map<ProductSortField, String> =
            mapOf(
                ProductSortField.CREATED_AT to "createdAt",
                ProductSortField.PRICE to "price",
                ProductSortField.NAME to "name",
                ProductSortField.AVERAGE_RATING to "averageRating",
            )
    }
}
