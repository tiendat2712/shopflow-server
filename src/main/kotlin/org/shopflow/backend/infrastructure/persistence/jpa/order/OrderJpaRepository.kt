package org.shopflow.backend.infrastructure.persistence.jpa.order

import org.shopflow.backend.infrastructure.persistence.entity.order.OrderJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, String>
