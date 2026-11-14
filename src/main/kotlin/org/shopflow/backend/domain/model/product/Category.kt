package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.event.product.CategoryCreatedEvent
import org.shopflow.backend.domain.exception.DomainValidationException
import java.time.Instant

data class Category(
    val id: CategoryId,
    val name: String,
    val slug: Slug,
    val parentId: CategoryId? = null,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        if (name.isBlank()) {
            throw DomainValidationException("Category name cannot be blank.")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw DomainValidationException("Category name must not exceed $MAX_NAME_LENGTH characters.")
        }
        if (slug.value.length > Slug.CATEGORY_MAX_LENGTH) {
            throw DomainValidationException("Category slug must not exceed ${Slug.CATEGORY_MAX_LENGTH} characters.")
        }
        if (parentId != null && parentId == id) {
            throw DomainValidationException("Category cannot reference itself as parent.")
        }
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            throw DomainValidationException("Category description must not exceed $MAX_DESCRIPTION_LENGTH characters.")
        }
        if (displayOrder < 0) {
            throw DomainValidationException("Category display order cannot be negative.")
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 500

        fun create(
            name: String,
            slug: Slug,
            now: Instant,
            id: CategoryId = CategoryId.generate(),
            parentId: CategoryId? = null,
            description: String? = null,
            displayOrder: Int = 0,
            isActive: Boolean = true,
        ): Pair<Category, CategoryCreatedEvent> {
            val normalizedName = name.trim()
            val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
            val category =
                Category(
                    id = id,
                    name = normalizedName,
                    slug = slug,
                    parentId = parentId,
                    description = normalizedDescription,
                    displayOrder = displayOrder,
                    isActive = isActive,
                    createdAt = now,
                    updatedAt = now,
                )
            val event =
                CategoryCreatedEvent(
                    categoryId = category.id,
                    name = category.name,
                    slug = category.slug,
                    occurredAt = now,
                )
            return category to event
        }
    }
}
