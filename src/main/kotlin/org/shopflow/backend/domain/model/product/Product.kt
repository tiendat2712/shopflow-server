package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.event.product.ProductArchivedEvent
import org.shopflow.backend.domain.event.product.ProductCreatedEvent
import org.shopflow.backend.domain.event.product.ProductUpdatedEvent
import org.shopflow.backend.domain.exception.DomainValidationException
import java.time.Instant

data class Product(
    val id: ProductId,
    val categoryId: CategoryId,
    val name: String,
    val slug: Slug,
    val sku: Sku,
    val description: String? = null,
    val price: Money,
    val stockQuantity: Int = 0,
    val averageRating: RatingAverage = RatingAverage.ZERO,
    val reviewCount: Int = 0,
    val imageUrl: String? = null,
    val status: ProductStatus = ProductStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        if (name.isBlank()) {
            throw DomainValidationException("Product name cannot be blank.")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw DomainValidationException("Product name must not exceed $MAX_NAME_LENGTH characters.")
        }
        if (stockQuantity < 0) {
            throw DomainValidationException("Stock quantity cannot be negative.")
        }
        if (reviewCount < 0) {
            throw DomainValidationException("Review count cannot be negative.")
        }
        if (imageUrl != null && imageUrl.length > MAX_IMAGE_URL_LENGTH) {
            throw DomainValidationException("Product image URL must not exceed $MAX_IMAGE_URL_LENGTH characters.")
        }
    }

    fun update(
        categoryId: CategoryId,
        name: String,
        slug: Slug,
        sku: Sku,
        description: String?,
        price: Money,
        stockQuantity: Int,
        imageUrl: String?,
        status: ProductStatus,
        now: Instant,
    ): Pair<Product, ProductUpdatedEvent> {
        val updated =
            copy(
                categoryId = categoryId,
                name = name.trim(),
                slug = slug,
                sku = sku,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                price = price,
                stockQuantity = stockQuantity,
                imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                status = status,
                updatedAt = now,
            )
        val event =
            ProductUpdatedEvent(
                productId = updated.id,
                price = updated.price,
                stockQuantity = updated.stockQuantity,
                status = updated.status,
                occurredAt = now,
            )
        return updated to event
    }

    fun archive(now: Instant): Pair<Product, ProductArchivedEvent> {
        val archived =
            copy(
                status = ProductStatus.ARCHIVED,
                updatedAt = now,
            )
        val event =
            ProductArchivedEvent(
                productId = archived.id,
                occurredAt = now,
            )
        return archived to event
    }

    companion object {
        const val MAX_NAME_LENGTH = 255
        const val MAX_IMAGE_URL_LENGTH = 500

        fun create(
            categoryId: CategoryId,
            name: String,
            slug: Slug,
            sku: Sku,
            price: Money,
            now: Instant,
            id: ProductId = ProductId.generate(),
            description: String? = null,
            stockQuantity: Int = 0,
            averageRating: RatingAverage = RatingAverage.ZERO,
            reviewCount: Int = 0,
            imageUrl: String? = null,
            status: ProductStatus = ProductStatus.ACTIVE,
        ): Pair<Product, ProductCreatedEvent> {
            val product =
                Product(
                    id = id,
                    categoryId = categoryId,
                    name = name.trim(),
                    slug = slug,
                    sku = sku,
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    price = price,
                    stockQuantity = stockQuantity,
                    averageRating = averageRating,
                    reviewCount = reviewCount,
                    imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                    status = status,
                    createdAt = now,
                    updatedAt = now,
                )
            val event =
                ProductCreatedEvent(
                    productId = product.id,
                    categoryId = product.categoryId,
                    sku = product.sku,
                    price = product.price,
                    occurredAt = now,
                )
            return product to event
        }
    }
}
