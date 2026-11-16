package org.shopflow.backend.domain.repository.product

import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug

interface ProductRepository {
    fun save(product: Product): Product

    fun update(product: Product): Product

    fun saveAll(products: Collection<Product>): List<Product>

    fun findById(id: ProductId): Product?

    fun findBySku(sku: Sku): Product?

    fun existsBySku(sku: Sku): Boolean

    fun existsBySlug(slug: Slug): Boolean

    fun findExistingIds(ids: Collection<ProductId>): Set<ProductId>

    fun findExistingSkus(skus: Collection<Sku>): Set<Sku>

    fun findExistingSlugs(slugs: Collection<Slug>): Set<Slug>

    fun updateImageUrlByCategoryId(
        categoryId: CategoryId,
        imageUrl: String,
    ): Int
}
