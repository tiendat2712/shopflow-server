package org.shopflow.backend.application.dto.product

import java.math.BigDecimal

data class ProductSummaryDto(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val name: String,
    val slug: String,
    val sku: String,
    val price: BigDecimal,
    val stockQuantity: Int,
    val averageRating: BigDecimal,
    val reviewCount: Int,
    val imageUrl: String?,
    val status: String,
    val version: Long,
)
