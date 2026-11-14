package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import java.time.Instant

data class ProductUpdatedEvent(
    val productId: ProductId,
    val price: Money,
    val stockQuantity: Int,
    val status: ProductStatus,
    override val occurredAt: Instant,
) : DomainEvent
