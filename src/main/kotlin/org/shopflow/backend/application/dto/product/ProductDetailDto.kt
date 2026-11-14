package org.shopflow.backend.application.dto.product

import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.Product
import java.math.BigDecimal
import java.time.Instant

data class ProductDetailDto(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val categorySlug: String,
    val name: String,
    val slug: String,
    val sku: String,
    val description: String?,
    val price: BigDecimal,
    val stockQuantity: Int,
    val averageRating: BigDecimal,
    val reviewCount: Int,
    val imageUrl: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Product.toDetailDto(category: Category): ProductDetailDto =
    ProductDetailDto(
        id = id.value.toString(),
        categoryId = categoryId.value.toString(),
        categoryName = category.name,
        categorySlug = category.slug.value,
        name = name,
        slug = slug.value,
        sku = sku.value,
        description = description,
        price = price.amount,
        stockQuantity = stockQuantity,
        averageRating = averageRating.value,
        reviewCount = reviewCount,
        imageUrl = imageUrl,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
