package org.shopflow.backend.application.command.product

data class DeleteProductCommand(
    val productId: String,
    val expectedVersion: Long,
)
