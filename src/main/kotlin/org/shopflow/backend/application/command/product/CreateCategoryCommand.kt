package org.shopflow.backend.application.command.product

data class CreateCategoryCommand(
    val name: String,
    val slug: String? = null,
    val parentId: String? = null,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)
