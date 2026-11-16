package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.ProductId
import java.time.Instant

data class ProductMarkedOutOfStockEvent(
    val productId: ProductId,
    override val occurredAt: Instant,
) : DomainEvent
