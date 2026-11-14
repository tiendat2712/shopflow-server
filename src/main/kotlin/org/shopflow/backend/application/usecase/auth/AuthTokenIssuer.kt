package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.dto.auth.AuthResult
import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.stereotype.Component

/**
 * Shared application helper to issue a JWT token pair and persist the hashed refresh token.
 */
@Component
class AuthTokenIssuer(
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val timeProvider: TimeProvider,
) {
    fun issueAndStoreTokens(user: User): AuthResult {
        val now = timeProvider.now()
        val accessToken =
            tokenProvider.generateAccessToken(
                userId = user.id,
                roles = user.getRoleNames(),
                permissions = user.getAllPermissions(),
            )
        val refreshToken = tokenProvider.generateRefreshToken(user.id)
        val hashedToken = tokenProvider.hashRefreshToken(refreshToken)
        val expiresAt = now.plusMillis(tokenProvider.refreshTokenValidityMs)

        refreshTokenRepository.save(
            RefreshToken(
                userId = user.id,
                hashedToken = hashedToken,
                expiresAt = expiresAt,
                createdAt = now,
            ),
        )

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }
}
