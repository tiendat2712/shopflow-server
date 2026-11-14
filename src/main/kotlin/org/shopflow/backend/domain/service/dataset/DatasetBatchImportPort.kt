package org.shopflow.backend.domain.service.dataset

import java.math.BigDecimal
import java.time.Instant

data class OrderSeedRow(
    val id: String,
    val userId: String,
    val orderDate: Instant,
    val orderStatus: String,
    val totalAmount: BigDecimal,
)

data class OrderItemSeedRow(
    val id: String,
    val orderId: String,
    val productId: String,
    val userId: String,
    val quantity: Int,
    val itemPrice: BigDecimal,
    val itemTotal: BigDecimal,
)

data class ReviewSeedRow(
    val id: String,
    val orderId: String,
    val productId: String,
    val userId: String,
    val rating: Int,
    val reviewText: String?,
    val reviewDate: Instant,
)

data class UserEventSeedRow(
    val id: String,
    val userId: String,
    val productId: String,
    val eventType: String,
    val eventTimestamp: Instant,
)

/**
 * Pure domain/application port for idempotent high-speed batch seeding of historical dataset tables.
 */
interface DatasetBatchImportPort {
    fun insertOrdersIgnore(
        rows: List<OrderSeedRow>,
        auditedAt: Instant,
    ): Int

    fun insertOrderItemsIgnore(
        rows: List<OrderItemSeedRow>,
        auditedAt: Instant,
    ): Int

    fun insertReviewsIgnore(
        rows: List<ReviewSeedRow>,
        auditedAt: Instant,
    ): Int

    fun syncProductReviewCounts(): Int

    fun insertUserEventsIgnore(
        rows: List<UserEventSeedRow>,
        auditedAt: Instant,
    ): Int
}
