package org.shopflow.backend.domain.event.product

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Slug
import java.time.Instant

data class CategoryCreatedEvent(
    val categoryId: CategoryId,
    val name: String,
    val slug: Slug,
    override val occurredAt: Instant,
) : DomainEvent
