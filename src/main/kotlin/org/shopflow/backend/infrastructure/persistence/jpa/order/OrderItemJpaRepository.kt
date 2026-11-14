package org.shopflow.backend.infrastructure.persistence.jpa.order

import org.shopflow.backend.infrastructure.persistence.entity.order.OrderItemJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderItemJpaRepository : JpaRepository<OrderItemJpaEntity, String>
