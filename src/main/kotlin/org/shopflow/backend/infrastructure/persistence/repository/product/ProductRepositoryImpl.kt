package org.shopflow.backend.infrastructure.persistence.repository.product

import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.infrastructure.persistence.jpa.product.ProductJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.product.ProductPersistenceMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductRepositoryImpl(
    private val jpaRepository: ProductJpaRepository,
    private val mapper: ProductPersistenceMapper,
) : ProductRepository {
    override fun save(product: Product): Product {
        val existing = jpaRepository.findById(product.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    categoryId = product.categoryId.value.toString()
                    name = product.name
                    slug = product.slug.value
                    sku = product.sku.value
                    description = product.description
                    price = product.price.amount
                    stockQuantity = product.stockQuantity
                    averageRating = product.averageRating.value
                    reviewCount = product.reviewCount
                    imageUrl = product.imageUrl
                    status = product.status
                }
            } else {
                mapper.toEntity(product)
            }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity))
    }

    @Transactional
    override fun saveAll(products: Collection<Product>): List<Product> {
        if (products.isEmpty()) return emptyList()
        val entities = products.map(mapper::toEntity)
        return jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
    }

    override fun findById(id: ProductId): Product? = jpaRepository.findById(id.value.toString()).orElse(null)?.let(mapper::toDomain)

    override fun findBySku(sku: Sku): Product? = jpaRepository.findBySku(sku.value)?.let(mapper::toDomain)

    override fun existsBySku(sku: Sku): Boolean = jpaRepository.existsBySku(sku.value)

    override fun existsBySlug(slug: Slug): Boolean = jpaRepository.existsBySlug(slug.value)

    override fun findExistingIds(ids: Collection<ProductId>): Set<ProductId> {
        if (ids.isEmpty()) return emptySet()
        return jpaRepository
            .findExistingIds(ids.map { it.value.toString() })
            .mapTo(hashSetOf()) { ProductId.fromString(it) }
    }

    override fun findExistingSkus(skus: Collection<Sku>): Set<Sku> {
        if (skus.isEmpty()) return emptySet()
        return jpaRepository
            .findExistingSkus(skus.map { it.value })
            .mapTo(hashSetOf()) { Sku(it) }
    }

    override fun findExistingSlugs(slugs: Collection<Slug>): Set<Slug> {
        if (slugs.isEmpty()) return emptySet()
        return jpaRepository
            .findExistingSlugs(slugs.map { it.value })
            .mapTo(hashSetOf()) { Slug(it) }
    }

    @Transactional
    override fun updateImageUrlByCategoryId(
        categoryId: CategoryId,
        imageUrl: String,
    ): Int = jpaRepository.updateImageUrlByCategoryId(categoryId.value.toString(), imageUrl)
}
