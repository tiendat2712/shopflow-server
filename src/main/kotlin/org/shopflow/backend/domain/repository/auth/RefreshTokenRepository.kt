package org.shopflow.backend.domain.repository.auth

import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId

interface RefreshTokenRepository {
    fun findByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): RefreshToken?

    fun deleteByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    )

    fun deleteAllByUserId(userId: UserId)

    fun save(refreshToken: RefreshToken): RefreshToken
}
