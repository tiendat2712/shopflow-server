package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.InvalidTokenException
import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a persisted hashed refresh token for token rotation.
 */
data class RefreshToken(
    val id: UUID = UUID.randomUUID(),
    val userId: UserId,
    val hashedToken: String,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)

    fun ensureNotExpired(now: Instant) {
        if (isExpired(now)) {
            throw InvalidTokenException("Refresh token has expired.")
        }
    }
}
