package org.shopflow.backend.infrastructure.persistence.entity.review

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.time.Instant

@Entity
@Table(
    name = "reviews",
    indexes = [
        Index(name = "idx_reviews_order_id", columnList = "order_id"),
        Index(name = "idx_reviews_product_id", columnList = "product_id"),
        Index(name = "idx_reviews_user_id", columnList = "user_id"),
    ],
)
class ReviewJpaEntity(
    id: String,
    @field:Column(name = "order_id", nullable = false, length = 36)
    var orderId: String,
    @field:Column(name = "product_id", nullable = false, length = 36)
    var productId: String,
    @field:Column(name = "user_id", nullable = false, length = 36)
    var userId: String,
    @field:Column(name = "rating", nullable = false)
    var rating: Int,
    @field:Column(name = "review_text", columnDefinition = "TEXT")
    var reviewText: String? = null,
    @field:Column(name = "review_date", nullable = false)
    var reviewDate: Instant,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
