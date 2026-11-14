package org.shopflow.backend.infrastructure.persistence.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.time.Instant

@Entity
@Table(
    name = "refresh_tokens",
    indexes = [
        Index(name = "idx_refresh_tokens_user_hash", columnList = "user_id, hashed_token", unique = true),
        Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at"),
    ],
)
class RefreshTokenJpaEntity(
    id: String,
    @field:Column(name = "user_id", nullable = false, length = 36)
    var userId: String,
    @field:Column(name = "hashed_token", nullable = false, length = 255)
    var hashedToken: String,
    @field:Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshTokenJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
