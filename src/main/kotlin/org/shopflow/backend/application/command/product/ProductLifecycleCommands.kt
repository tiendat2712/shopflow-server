package org.shopflow.backend.application.command.product

data class PublishProductCommand(
    val productId: String,
    val expectedVersion: Long,
)

data class RestoreProductCommand(
    val productId: String,
    val expectedVersion: Long,
)
