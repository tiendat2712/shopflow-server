package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import java.time.Instant

data class ProductRestoredEvent(
    val productId: ProductId,
    val restoredStatus: ProductStatus = ProductStatus.DRAFT,
    override val occurredAt: Instant,
) : DomainEvent
