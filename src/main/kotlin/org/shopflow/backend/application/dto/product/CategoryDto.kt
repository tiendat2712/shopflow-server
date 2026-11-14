package org.shopflow.backend.application.dto.product

import org.shopflow.backend.domain.model.product.Category
import java.time.Instant

data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val parentId: String?,
    val description: String?,
    val displayOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
)

fun Category.toDto(): CategoryDto =
    CategoryDto(
        id = id.value.toString(),
        name = name,
        slug = slug.value,
        parentId = parentId?.value?.toString(),
        description = description,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt,
    )
