package org.shopflow.backend.application.command.product

import java.math.BigDecimal

data class UpdateProductCommand(
    val productId: String,
    val categoryId: String,
    val name: String,
    val slug: String? = null,
    val sku: String,
    val description: String? = null,
    val price: BigDecimal,
    val stockQuantity: Int = 0,
    val imageUrl: String? = null,
    val status: String = "ACTIVE",
)
