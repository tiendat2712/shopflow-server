package org.shopflow.backend.infrastructure.persistence.repository.product

import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.infrastructure.persistence.jpa.product.CategoryJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.product.CategoryPersistenceMapper
import org.shopflow.backend.infrastructure.persistence.support.UniqueConstraintViolationTranslator
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CategoryRepositoryImpl(
    private val jpaRepository: CategoryJpaRepository,
    private val mapper: CategoryPersistenceMapper,
    private val constraintTranslator: UniqueConstraintViolationTranslator,
) : CategoryRepository {
    @Transactional
    override fun save(category: Category): Category {
        val existing = jpaRepository.findById(category.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.also { mapper.updateEntity(category, it) }
            } else {
                mapper.toEntity(category)
            }
        return try {
            mapper.toDomain(jpaRepository.saveAndFlush(entity))
        } catch (ex: DataIntegrityViolationException) {
            translateCategoryIntegrityViolation(ex, category.slug.value)
        }
    }

    @Transactional
    override fun saveAll(categories: Collection<Category>): List<Category> {
        if (categories.isEmpty()) return emptyList()
        val entities = categories.map(mapper::toEntity)
        return try {
            jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
        } catch (ex: DataIntegrityViolationException) {
            translateCategoryIntegrityViolation(ex)
        }
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

    private fun translateCategoryIntegrityViolation(
        ex: DataIntegrityViolationException,
        slugValue: String? = null,
    ): Nothing {
        if (constraintTranslator.resolveViolatedColumn(ex, TABLE_NAME) == SLUG_COLUMN) {
            if (slugValue != null) {
                throw DuplicateCategorySlugException("Category slug '$slugValue' already exists.", cause = ex)
            }
            throw DuplicateCategorySlugException(cause = ex)
        }
        throw ex
    }

    companion object {
        private const val TABLE_NAME = "categories"
        private const val SLUG_COLUMN = "slug"
    }
}
