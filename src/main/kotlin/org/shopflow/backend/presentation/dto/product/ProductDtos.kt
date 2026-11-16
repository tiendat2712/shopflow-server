package org.shopflow.backend.presentation.dto.product

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.shopflow.backend.presentation.validation.ValidPrice
import java.math.BigDecimal
import java.time.Instant

data class CreateProductRequest(
    @field:NotBlank(message = "Category ID is required.")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "Invalid category ID format.",
    )
    val categoryId: String,
    @field:NotBlank(message = "Product name is required.")
    @field:Size(max = 255, message = "Product name must not exceed 255 characters.")
    val name: String,
    @field:Size(max = 255, message = "Product slug must not exceed 255 characters.")
    val slug: String? = null,
    @field:NotBlank(message = "Product SKU is required.")
    @field:Size(max = 100, message = "Product SKU must not exceed 100 characters.")
    val sku: String,
    val description: String? = null,
    @field:NotNull(message = "Product price is required.")
    @field:ValidPrice
    val price: BigDecimal,
    @field:Min(value = 0, message = "Stock quantity cannot be negative.")
    val stockQuantity: Int = 0,
    @field:Size(max = 500, message = "Image URL must not exceed 500 characters.")
    val imageUrl: String? = null,
    val status: String = "ACTIVE",
)

data class UpdateProductRequest(
    @field:NotBlank(message = "Category ID is required.")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "Invalid category ID format.",
    )
    val categoryId: String,
    @field:NotBlank(message = "Product name is required.")
    @field:Size(max = 255, message = "Product name must not exceed 255 characters.")
    val name: String,
    @field:Size(max = 255, message = "Product slug must not exceed 255 characters.")
    val slug: String? = null,
    @field:NotBlank(message = "Product SKU is required.")
    @field:Size(max = 100, message = "Product SKU must not exceed 100 characters.")
    val sku: String,
    val description: String? = null,
    @field:NotNull(message = "Product price is required.")
    @field:ValidPrice
    val price: BigDecimal,
    @field:Min(value = 0, message = "Stock quantity cannot be negative.")
    val stockQuantity: Int = 0,
    @field:Size(max = 500, message = "Image URL must not exceed 500 characters.")
    val imageUrl: String? = null,
    val status: String? = null,
    @field:Min(value = 0, message = "Product version cannot be negative.")
    val version: Long? = null,
)

data class ProductSummaryResponse(
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

data class ProductDetailResponse(
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
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
