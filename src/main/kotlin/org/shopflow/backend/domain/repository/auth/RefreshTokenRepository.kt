package org.shopflow.backend.domain.repository.auth

import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId
import java.time.Instant

interface RefreshTokenRepository {
    fun findByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): RefreshToken?

    fun consumeIfValid(
        userId: UserId,
        hashedToken: String,
        now: Instant,
    ): Boolean

    fun deleteByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): Boolean

    fun deleteAllByUserId(userId: UserId)

    fun save(refreshToken: RefreshToken): RefreshToken
}
