package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import java.time.Instant

data class ProductPublishedEvent(
    val productId: ProductId,
    val previousStatus: ProductStatus,
    override val occurredAt: Instant,
) : DomainEvent
