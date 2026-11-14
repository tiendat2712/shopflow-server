package org.shopflow.backend.infrastructure.persistence.entity.analytics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.time.Instant

@Entity
@Table(
    name = "user_events",
    indexes = [
        Index(name = "idx_user_events_user_id", columnList = "user_id"),
        Index(name = "idx_user_events_product_id", columnList = "product_id"),
        Index(name = "idx_user_events_event_type", columnList = "event_type"),
        Index(name = "idx_user_events_event_timestamp", columnList = "event_timestamp"),
    ],
)
class UserEventJpaEntity(
    id: String,
    @field:Column(name = "user_id", nullable = false, length = 36)
    var userId: String,
    @field:Column(name = "product_id", nullable = false, length = 36)
    var productId: String,
    @field:Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,
    @field:Column(name = "event_timestamp", nullable = false)
    var eventTimestamp: Instant,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserEventJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
