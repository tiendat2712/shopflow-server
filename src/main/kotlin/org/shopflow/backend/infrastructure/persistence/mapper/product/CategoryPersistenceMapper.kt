package org.shopflow.backend.infrastructure.persistence.mapper.product

import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.infrastructure.persistence.entity.product.CategoryJpaEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class CategoryPersistenceMapper {
    fun toDomain(entity: CategoryJpaEntity): Category =
        Category(
            id = CategoryId(UUID.fromString(entity.getId())),
            name = entity.name,
            slug = Slug(entity.slug),
            parentId = entity.parentId?.let { CategoryId(UUID.fromString(it)) },
            description = entity.description,
            displayOrder = entity.displayOrder,
            isActive = entity.isActive,
            createdAt = entity.createdAt ?: Instant.EPOCH,
            updatedAt = entity.updatedAt ?: Instant.EPOCH,
        )

    fun toEntity(domain: Category): CategoryJpaEntity =
        CategoryJpaEntity(
            id = domain.id.value.toString(),
            name = domain.name,
            slug = domain.slug.value,
            parentId = domain.parentId?.value?.toString(),
            description = domain.description,
            displayOrder = domain.displayOrder,
            isActive = domain.isActive,
        )

    fun updateEntity(
        domain: Category,
        entity: CategoryJpaEntity,
    ) {
        entity.name = domain.name
        entity.slug = domain.slug.value
        entity.parentId = domain.parentId?.value?.toString()
        entity.description = domain.description
        entity.displayOrder = domain.displayOrder
        entity.isActive = domain.isActive
    }
}
