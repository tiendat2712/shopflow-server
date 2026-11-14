package org.shopflow.backend.infrastructure.persistence.jpa.product

import org.shopflow.backend.infrastructure.persistence.entity.product.CategoryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface CategoryJpaRepository :
    JpaRepository<CategoryJpaEntity, String>,
    JpaSpecificationExecutor<CategoryJpaEntity> {
    fun findBySlug(slug: String): CategoryJpaEntity?

    fun existsBySlug(slug: String): Boolean

    fun findAllBySlugIn(slugs: Collection<String>): List<CategoryJpaEntity>

    fun findAllByOrderByDisplayOrderAscNameAsc(): List<CategoryJpaEntity>

    fun findAllByIsActiveTrueOrderByDisplayOrderAscNameAsc(): List<CategoryJpaEntity>
}
