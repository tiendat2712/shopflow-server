package org.shopflow.backend.infrastructure.persistence.entity.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.math.BigDecimal

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_items_order_id", columnList = "order_id"),
        Index(name = "idx_order_items_product_id", columnList = "product_id"),
        Index(name = "idx_order_items_user_id", columnList = "user_id"),
    ],
)
class OrderItemJpaEntity(
    id: String,
    @field:Column(name = "order_id", nullable = false, length = 36)
    var orderId: String,
    @field:Column(name = "product_id", nullable = false, length = 36)
    var productId: String,
    @field:Column(name = "user_id", nullable = false, length = 36)
    var userId: String,
    @field:Column(name = "quantity", nullable = false)
    var quantity: Int,
    @field:Column(name = "item_price", nullable = false, precision = 15, scale = 2)
    var itemPrice: BigDecimal,
    @field:Column(name = "item_total", nullable = false, precision = 15, scale = 2)
    var itemTotal: BigDecimal,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderItemJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
