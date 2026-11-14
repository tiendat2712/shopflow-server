package org.shopflow.backend.infrastructure.persistence.repository.product

import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.infrastructure.persistence.jpa.product.CategoryJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.product.CategoryPersistenceMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CategoryRepositoryImpl(
    private val jpaRepository: CategoryJpaRepository,
    private val mapper: CategoryPersistenceMapper,
) : CategoryRepository {
    override fun save(category: Category): Category {
        val existing = jpaRepository.findById(category.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    name = category.name
                    slug = category.slug.value
                    parentId = category.parentId?.value?.toString()
                    description = category.description
                    displayOrder = category.displayOrder
                    isActive = category.isActive
                }
            } else {
                mapper.toEntity(category)
            }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity))
    }

    @Transactional
    override fun saveAll(categories: Collection<Category>): List<Category> {
        if (categories.isEmpty()) return emptyList()
        val entities = categories.map(mapper::toEntity)
        return jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
    }

    override fun findById(id: CategoryId): Category? = jpaRepository.findById(id.value.toString()).orElse(null)?.let(mapper::toDomain)

    override fun findBySlug(slug: Slug): Category? = jpaRepository.findBySlug(slug.value)?.let(mapper::toDomain)

    override fun findAllBySlugs(slugs: Collection<Slug>): Map<Slug, Category> {
        if (slugs.isEmpty()) return emptyMap()
        val rawSlugs = slugs.map { it.value }
        return jpaRepository
            .findAllBySlugIn(rawSlugs)
            .map(mapper::toDomain)
            .associateBy { it.slug }
    }

    override fun existsBySlug(slug: Slug): Boolean = jpaRepository.existsBySlug(slug.value)
}
