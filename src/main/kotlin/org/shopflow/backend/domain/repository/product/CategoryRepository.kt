package org.shopflow.backend.domain.repository.product

import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug

interface CategoryRepository {
    fun save(category: Category): Category

    fun saveAll(categories: Collection<Category>): List<Category>

    fun findById(id: CategoryId): Category?

    fun findBySlug(slug: Slug): Category?

    fun findAllBySlugs(slugs: Collection<Slug>): Map<Slug, Category>

    fun existsBySlug(slug: Slug): Boolean
}
