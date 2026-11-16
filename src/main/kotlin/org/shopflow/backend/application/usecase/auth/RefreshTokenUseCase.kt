package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.command.auth.RefreshTokenCommand
import org.shopflow.backend.application.dto.auth.AuthResult
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefreshTokenUseCase(
    private val tokenProvider: TokenProvider,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authTokenIssuer: AuthTokenIssuer,
    private val timeProvider: TimeProvider,
) {
    @Transactional
    fun execute(command: RefreshTokenCommand): AuthResult {
        if (!tokenProvider.validateRefreshToken(command.refreshToken)) {
            throw InvalidTokenException("Invalid refresh token.")
        }

        val userId = tokenProvider.extractUserId(command.refreshToken)
        val user =
            userRepository.findById(userId)
                ?: throw InvalidTokenException("Invalid refresh token.")

        user.ensureCanAuthenticate()

        val hashedToken = tokenProvider.hashRefreshToken(command.refreshToken)
        val consumed = refreshTokenRepository.consumeIfValid(user.id, hashedToken, timeProvider.now())
        if (!consumed) {
            throw InvalidTokenException("Refresh token not recognized (maybe used or expired?)")
        }

        return authTokenIssuer.issueAndStoreTokens(user)
    }
}
