package org.shopflow.backend.infrastructure.persistence.mapper.product

import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.RatingAverage
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.infrastructure.persistence.entity.product.ProductJpaEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ProductPersistenceMapper {
    fun toDomain(entity: ProductJpaEntity): Product =
        Product(
            id = ProductId(UUID.fromString(entity.getId())),
            categoryId = CategoryId(UUID.fromString(entity.categoryId)),
            name = entity.name,
            slug = Slug(entity.slug),
            sku = Sku(entity.sku),
            description = entity.description,
            price = Money.of(entity.price),
            stockQuantity = entity.stockQuantity,
            averageRating = RatingAverage.of(entity.averageRating),
            reviewCount = entity.reviewCount,
            imageUrl = entity.imageUrl,
            status = entity.status,
            createdAt = entity.createdAt ?: Instant.EPOCH,
            updatedAt = entity.updatedAt ?: Instant.EPOCH,
        )

    fun toEntity(domain: Product): ProductJpaEntity =
        ProductJpaEntity(
            id = domain.id.value.toString(),
            categoryId = domain.categoryId.value.toString(),
            name = domain.name,
            slug = domain.slug.value,
            sku = domain.sku.value,
            description = domain.description,
            price = domain.price.amount,
            stockQuantity = domain.stockQuantity,
            averageRating = domain.averageRating.value,
            reviewCount = domain.reviewCount,
            imageUrl = domain.imageUrl,
            status = domain.status,
        )
}
