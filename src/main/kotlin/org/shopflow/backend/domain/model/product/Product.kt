package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.event.product.ProductArchivedEvent
import org.shopflow.backend.domain.event.product.ProductCreatedEvent
import org.shopflow.backend.domain.event.product.ProductMarkedOutOfStockEvent
import org.shopflow.backend.domain.event.product.ProductPublishedEvent
import org.shopflow.backend.domain.event.product.ProductRestoredEvent
import org.shopflow.backend.domain.event.product.ProductUpdatedEvent
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import java.time.Instant

/**
 * Product aggregate root encapsulating catalog invariants and explicit lifecycle transitions.
 */
@ConsistentCopyVisibility
data class Product private constructor(
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
    val version: Long = 0L,
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
        if (version < 0) {
            throw DomainValidationException("Product version cannot be negative.")
        }
    }

    fun ensureVersionMatches(expectedVersion: Long) {
        if (expectedVersion < 0) {
            throw DomainValidationException("Expected product version cannot be negative.")
        }
        if (version != expectedVersion) {
            throw ProductConcurrencyConflictException(
                "Product '${id.value}' version mismatch: expected $expectedVersion, current is $version.",
            )
        }
    }

    fun updateCatalogDetails(
        categoryId: CategoryId,
        name: String,
        slug: Slug,
        sku: Sku,
        description: String?,
        price: Money,
        stockQuantity: Int,
        imageUrl: String?,
        expectedVersion: Long,
        now: Instant,
    ): Pair<Product, ProductUpdatedEvent?> {
        ensureVersionMatches(expectedVersion)
        if (status == ProductStatus.ARCHIVED) {
            throw DomainValidationException("Cannot update catalog details of an archived product. Restore it first.")
        }
        val normalizedName = name.trim()
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }

        val hasChanges =
            this.categoryId != categoryId ||
                this.name != normalizedName ||
                this.slug != slug ||
                this.sku != sku ||
                this.description != normalizedDescription ||
                this.price != price ||
                this.stockQuantity != stockQuantity ||
                this.imageUrl != normalizedImageUrl

        if (!hasChanges) {
            return this to null
        }

        val updated =
            copy(
                categoryId = categoryId,
                name = normalizedName,
                slug = slug,
                sku = sku,
                description = normalizedDescription,
                price = price,
                stockQuantity = stockQuantity,
                imageUrl = normalizedImageUrl,
                status = this.status,
                version = this.version,
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

    fun publish(
        expectedVersion: Long,
        now: Instant,
    ): Pair<Product, ProductPublishedEvent?> {
        ensureVersionMatches(expectedVersion)
        if (status == ProductStatus.ACTIVE) {
            return this to null
        }
        if (!status.canTransitionTo(ProductStatus.ACTIVE)) {
            throw DomainValidationException(
                "Cannot publish product from status $status. Restore archived products to DRAFT first.",
            )
        }
        val published =
            copy(
                status = ProductStatus.ACTIVE,
                version = this.version,
                updatedAt = now,
            )
        val event =
            ProductPublishedEvent(
                productId = published.id,
                previousStatus = status,
                occurredAt = now,
            )
        return published to event
    }

    fun markOutOfStock(
        expectedVersion: Long,
        now: Instant,
    ): Pair<Product, ProductMarkedOutOfStockEvent?> {
        ensureVersionMatches(expectedVersion)
        if (status == ProductStatus.OUT_OF_STOCK) {
            return this to null
        }
        if (!status.canTransitionTo(ProductStatus.OUT_OF_STOCK)) {
            throw DomainValidationException("Cannot mark product out of stock from status $status.")
        }
        val outOfStock =
            copy(
                status = ProductStatus.OUT_OF_STOCK,
                version = this.version,
                updatedAt = now,
            )
        val event =
            ProductMarkedOutOfStockEvent(
                productId = outOfStock.id,
                occurredAt = now,
            )
        return outOfStock to event
    }

    fun archive(
        expectedVersion: Long,
        now: Instant,
    ): Pair<Product, ProductArchivedEvent?> {
        ensureVersionMatches(expectedVersion)
        if (status == ProductStatus.ARCHIVED) {
            return this to null
        }
        if (!status.canTransitionTo(ProductStatus.ARCHIVED)) {
            throw DomainValidationException("Cannot archive product from status $status.")
        }
        val archived =
            copy(
                status = ProductStatus.ARCHIVED,
                version = this.version,
                updatedAt = now,
            )
        val event =
            ProductArchivedEvent(
                productId = archived.id,
                occurredAt = now,
            )
        return archived to event
    }

    fun restore(
        expectedVersion: Long,
        now: Instant,
    ): Pair<Product, ProductRestoredEvent> {
        ensureVersionMatches(expectedVersion)
        if (!status.canTransitionTo(ProductStatus.DRAFT)) {
            throw DomainValidationException("Only archived products can be restored (current status: $status).")
        }
        val restored =
            copy(
                status = ProductStatus.DRAFT,
                version = this.version,
                updatedAt = now,
            )
        val event =
            ProductRestoredEvent(
                productId = restored.id,
                restoredStatus = ProductStatus.DRAFT,
                occurredAt = now,
            )
        return restored to event
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
                    version = 0L,
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

        fun rehydrate(
            id: ProductId,
            categoryId: CategoryId,
            name: String,
            slug: Slug,
            sku: Sku,
            description: String? = null,
            price: Money,
            stockQuantity: Int = 0,
            averageRating: RatingAverage = RatingAverage.ZERO,
            reviewCount: Int = 0,
            imageUrl: String? = null,
            status: ProductStatus = ProductStatus.ACTIVE,
            version: Long = 0L,
            createdAt: Instant,
            updatedAt: Instant,
        ): Product =
            Product(
                id = id,
                categoryId = categoryId,
                name = name,
                slug = slug,
                sku = sku,
                description = description,
                price = price,
                stockQuantity = stockQuantity,
                averageRating = averageRating,
                reviewCount = reviewCount,
                imageUrl = imageUrl,
                status = status,
                version = version,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
    }
}
