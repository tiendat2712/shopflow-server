package org.shopflow.backend.presentation.dto.product

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateCategoryRequest(
    @field:NotBlank(message = "Category name is required.")
    @field:Size(max = 120, message = "Category name must not exceed 120 characters.")
    val name: String,
    @field:Size(max = 140, message = "Category slug must not exceed 140 characters.")
    val slug: String? = null,
    @field:Pattern(
        regexp = "^\\s*$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "Invalid category ID format.",
    )
    val parentId: String? = null,
    @field:Size(max = 500, message = "Category description must not exceed 500 characters.")
    val description: String? = null,
    @field:Min(value = 0, message = "Display order cannot be negative.")
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)

data class CategoryResponse(
    val id: String,
    val name: String,
    val slug: String,
    val parentId: String?,
    val description: String?,
    val displayOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
)
