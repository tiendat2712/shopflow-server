package org.shopflow.backend.infrastructure.persistence.jpa.product

import org.shopflow.backend.infrastructure.persistence.entity.product.ProductJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository :
    JpaRepository<ProductJpaEntity, String>,
    JpaSpecificationExecutor<ProductJpaEntity> {
    fun findBySku(sku: String): ProductJpaEntity?

    fun findBySlug(slug: String): ProductJpaEntity?

    fun existsBySku(sku: String): Boolean

    fun existsBySlug(slug: String): Boolean

    @Query(value = "SELECT id FROM products WHERE id IN (:ids)", nativeQuery = true)
    fun findExistingIds(
        @Param("ids") ids: Collection<String>,
    ): Set<String>

    @Query("SELECT p.sku FROM ProductJpaEntity p WHERE p.sku IN :skus")
    fun findExistingSkus(
        @Param("skus") skus: Collection<String>,
    ): Set<String>

    @Query("SELECT p.slug FROM ProductJpaEntity p WHERE p.slug IN :slugs")
    fun findExistingSlugs(
        @Param("slugs") slugs: Collection<String>,
    ): Set<String>

    @Modifying
    @Query("UPDATE ProductJpaEntity p SET p.imageUrl = :imageUrl WHERE p.categoryId = :categoryId")
    fun updateImageUrlByCategoryId(
        @Param("categoryId") categoryId: String,
        @Param("imageUrl") imageUrl: String,
    ): Int
}
