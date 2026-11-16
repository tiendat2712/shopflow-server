package org.shopflow.backend.infrastructure.persistence.repository.product

import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.infrastructure.persistence.entity.product.ProductJpaEntity
import org.shopflow.backend.infrastructure.persistence.jpa.product.ProductJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.product.ProductPersistenceMapper
import org.shopflow.backend.infrastructure.persistence.support.UniqueConstraintViolationTranslator
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductRepositoryImpl(
    private val jpaRepository: ProductJpaRepository,
    private val mapper: ProductPersistenceMapper,
    private val constraintTranslator: UniqueConstraintViolationTranslator,
) : ProductRepository {
    @Transactional
    override fun save(product: Product): Product {
        val existing = jpaRepository.findById(product.id.value.toString()).orElse(null)
        if (existing != null) {
            return updateManagedEntity(product, existing)
        }
        return try {
            mapper.toDomain(jpaRepository.saveAndFlush(mapper.toEntity(product)))
        } catch (ex: DataIntegrityViolationException) {
            translateProductIntegrityViolation(ex, product.sku.value, product.slug.value)
        }
    }

    @Transactional
    override fun update(product: Product): Product {
        val id = product.id.value.toString()
        val existing =
            jpaRepository
                .findById(id)
                .orElseThrow { ProductNotFoundException() }
        return updateManagedEntity(product, existing)
    }

    private fun updateManagedEntity(
        product: Product,
        existing: ProductJpaEntity,
    ): Product {
        val id = product.id.value.toString()
        if (existing.version != product.version) {
            throw ProductConcurrencyConflictException(
                "Product '$id' was modified concurrently (expected version ${product.version}).",
            )
        }
        mapper.updateEntity(product, existing)
        return try {
            mapper.toDomain(jpaRepository.saveAndFlush(existing))
        } catch (ex: OptimisticLockingFailureException) {
            throw ProductConcurrencyConflictException(cause = ex)
        } catch (ex: PessimisticLockingFailureException) {
            throw ProductConcurrencyConflictException(cause = ex)
        } catch (ex: DataIntegrityViolationException) {
            translateProductIntegrityViolation(ex, product.sku.value, product.slug.value)
        }
    }

    @Transactional
    override fun saveAll(products: Collection<Product>): List<Product> {
        if (products.isEmpty()) return emptyList()
        val entities = products.map(mapper::toEntity)
        return try {
            jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
        } catch (ex: DataIntegrityViolationException) {
            translateProductIntegrityViolation(ex)
        }
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

    private fun translateProductIntegrityViolation(
        ex: DataIntegrityViolationException,
        skuValue: String? = null,
        slugValue: String? = null,
    ): Nothing {
        when (constraintTranslator.resolveViolatedColumn(ex, TABLE_NAME)) {
            SKU_COLUMN ->
                if (skuValue != null) {
                    throw DuplicateProductSkuException("Product SKU '$skuValue' already exists.", cause = ex)
                } else {
                    throw DuplicateProductSkuException(cause = ex)
                }
            SLUG_COLUMN ->
                if (slugValue != null) {
                    throw DuplicateProductSlugException("Product slug '$slugValue' already exists.", cause = ex)
                } else {
                    throw DuplicateProductSlugException(cause = ex)
                }
            else -> throw ex
        }
    }

    companion object {
        private const val TABLE_NAME = "products"
        private const val SKU_COLUMN = "sku"
        private const val SLUG_COLUMN = "slug"
    }
}
