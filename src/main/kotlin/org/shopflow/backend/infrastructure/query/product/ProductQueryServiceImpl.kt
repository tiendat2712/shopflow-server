package org.shopflow.backend.infrastructure.query.product

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.query.product.ListAdminProductsQuery
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.application.query.product.ProductSortField
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.infrastructure.persistence.entity.product.CategoryJpaEntity
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
    override fun listPublicProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto> {
        val effectiveStatuses = query.statuses.intersect(ProductStatus.PUBLIC_STATUSES)
        if (effectiveStatuses.isEmpty()) {
            return emptyPage(query.page, query.size)
        }

        val resolvedCategoryId =
            when {
                query.categorySlug != null -> {
                    val category = categoryJpaRepository.findBySlug(query.categorySlug)
                    if (category == null || !category.isActive || (query.categoryId != null && category.getId() != query.categoryId)) {
                        return emptyPage(query.page, query.size)
                    }
                    category.getId()
                }
                query.categoryId != null -> {
                    val category = categoryJpaRepository.findById(query.categoryId).orElse(null)
                    if (category == null || !category.isActive) {
                        return emptyPage(query.page, query.size)
                    }
                    category.getId()
                }
                else -> null
            }

        val specification =
            Specification<ProductJpaEntity> { root, criteriaQuery, cb ->
                val predicates = mutableListOf<Predicate>()
                predicates += root.get<ProductStatus>("status").`in`(effectiveStatuses)

                val activeCategorySubquery = criteriaQuery.subquery(Int::class.java)
                val categoryRoot = activeCategorySubquery.from(CategoryJpaEntity::class.java)
                activeCategorySubquery
                    .select(cb.literal(1))
                    .where(
                        cb.equal(categoryRoot.get<String>("entityId"), root.get<String>("categoryId")),
                        cb.isTrue(categoryRoot.get("isActive")),
                    )
                predicates += cb.exists(activeCategorySubquery)

                appendCommonProductPredicates(
                    predicates = predicates,
                    root = root,
                    cb = cb,
                    categoryId = resolvedCategoryId,
                    keyword = query.keyword,
                    minPrice = query.minPrice,
                    maxPrice = query.maxPrice,
                    minRating = query.minRating,
                )
                cb.and(*predicates.toTypedArray())
            }

        return executeProductPageQuery(
            specification = specification,
            page = query.page,
            size = query.size,
            sortField = query.sortField,
            sortDirection = query.sortDirection,
        )
    }

    override fun findPublicProductDetail(idOrSlug: String): ProductDetailDto? {
        val (entity, category) = findProductEntityWithCategory(idOrSlug) ?: return null
        if (!entity.status.isPubliclyVisible || category == null || !category.isActive) {
            return null
        }
        return entity.toDetailDto(category)
    }

    override fun listAdminProducts(query: ListAdminProductsQuery): PageResponseDto<ProductSummaryDto> {
        val resolvedCategoryId =
            if (query.categorySlug != null) {
                val category = categoryJpaRepository.findBySlug(query.categorySlug)
                if (category == null || (query.categoryId != null && category.getId() != query.categoryId)) {
                    return emptyPage(query.page, query.size)
                }
                category.getId()
            } else {
                query.categoryId
            }

        val specification =
            Specification<ProductJpaEntity> { root, _, cb ->
                val predicates = mutableListOf<Predicate>()
                if (query.targetStatus != null) {
                    predicates += cb.equal(root.get<ProductStatus>("status"), query.targetStatus)
                }
                appendCommonProductPredicates(
                    predicates = predicates,
                    root = root,
                    cb = cb,
                    categoryId = resolvedCategoryId,
                    keyword = query.keyword,
                    minPrice = query.minPrice,
                    maxPrice = query.maxPrice,
                    minRating = query.minRating,
                )
                cb.and(*predicates.toTypedArray())
            }

        return executeProductPageQuery(
            specification = specification,
            page = query.page,
            size = query.size,
            sortField = query.sortField,
            sortDirection = query.sortDirection,
        )
    }

    override fun findAdminProductDetail(idOrSlug: String): ProductDetailDto? {
        val (entity, category) = findProductEntityWithCategory(idOrSlug) ?: return null
        return entity.toDetailDto(category)
    }

    private fun findProductEntityWithCategory(idOrSlug: String): Pair<ProductJpaEntity, CategoryJpaEntity?>? {
        val parsedUuid = runCatching { UUID.fromString(idOrSlug) }.getOrNull()
        val entity =
            if (parsedUuid != null) {
                productJpaRepository.findById(parsedUuid.toString()).orElse(null)
                    ?: productJpaRepository.findBySlug(idOrSlug.lowercase())
            } else {
                productJpaRepository.findBySlug(idOrSlug.lowercase())
            } ?: return null

        val category = categoryJpaRepository.findById(entity.categoryId).orElse(null)
        return entity to category
    }

    private fun ProductJpaEntity.toDetailDto(category: CategoryJpaEntity?): ProductDetailDto =
        ProductDetailDto(
            id = getId(),
            categoryId = categoryId,
            categoryName = category?.name ?: "General",
            categorySlug = category?.slug ?: "general",
            name = name,
            slug = slug,
            sku = sku,
            description = description,
            price = price,
            stockQuantity = stockQuantity,
            averageRating = averageRating,
            reviewCount = reviewCount,
            imageUrl = imageUrl,
            status = status.name,
            version = version,
            createdAt = createdAt ?: Instant.EPOCH,
            updatedAt = updatedAt ?: Instant.EPOCH,
        )

    private fun executeProductPageQuery(
        specification: Specification<ProductJpaEntity>,
        page: Int,
        size: Int,
        sortField: ProductSortField,
        sortDirection: SortDirection,
    ): PageResponseDto<ProductSummaryDto> {
        val pageable = PageRequest.of(page, size, resolveSort(sortField, sortDirection))
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
                    version = entity.version,
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

    private fun appendCommonProductPredicates(
        predicates: MutableList<Predicate>,
        root: Root<ProductJpaEntity>,
        cb: CriteriaBuilder,
        categoryId: String?,
        keyword: String?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        minRating: BigDecimal?,
    ) {
        if (categoryId != null) {
            predicates += cb.equal(root.get<String>("categoryId"), categoryId)
        }
        if (keyword != null) {
            val pattern = "%${keyword.lowercase()}%"
            predicates +=
                cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("sku")), pattern),
                )
        }
        if (minPrice != null) {
            predicates += cb.greaterThanOrEqualTo(root.get<BigDecimal>("price"), minPrice)
        }
        if (maxPrice != null) {
            predicates += cb.lessThanOrEqualTo(root.get<BigDecimal>("price"), maxPrice)
        }
        if (minRating != null) {
            predicates += cb.greaterThanOrEqualTo(root.get<BigDecimal>("averageRating"), minRating)
        }
    }

    private fun emptyPage(
        page: Int,
        size: Int,
    ): PageResponseDto<ProductSummaryDto> =
        PageResponseDto(
            items = emptyList(),
            page = page,
            size = size,
            totalElements = 0L,
            totalPages = 0,
            hasNext = false,
            hasPrevious = false,
        )

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
