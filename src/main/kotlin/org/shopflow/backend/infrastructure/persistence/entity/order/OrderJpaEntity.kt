package org.shopflow.backend.infrastructure.persistence.entity.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_id", columnList = "user_id"),
        Index(name = "idx_orders_status", columnList = "order_status"),
        Index(name = "idx_orders_user_date", columnList = "user_id, order_date"),
    ],
)
class OrderJpaEntity(
    id: String,
    @field:Column(name = "user_id", nullable = false, length = 36)
    var userId: String,
    @field:Column(name = "order_date", nullable = false)
    var orderDate: Instant,
    @field:Column(name = "order_status", nullable = false, length = 32)
    var orderStatus: String,
    @field:Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    var totalAmount: BigDecimal,
    @field:Column(name = "shipping_address", nullable = true, length = 500)
    var shippingAddress: String? = null,
    @field:Column(name = "shipping_city", nullable = true, length = 100)
    var shippingCity: String? = null,
    @field:Column(name = "payment_method", nullable = true, length = 50)
    var paymentMethod: String? = null,
    @field:Column(name = "payment_status", nullable = true, length = 32)
    var paymentStatus: String? = null,
    @field:Version
    @field:Column(name = "version", nullable = false)
    var version: Long = 0L,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
