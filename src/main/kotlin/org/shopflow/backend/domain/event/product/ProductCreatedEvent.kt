package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import java.time.Instant

data class ProductCreatedEvent(
    val productId: ProductId,
    val categoryId: CategoryId,
    val sku: Sku,
    val price: Money,
    override val occurredAt: Instant,
) : DomainEvent
