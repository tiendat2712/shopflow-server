package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.command.auth.LogoutCommand
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LogoutUseCase(
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    @Transactional
    fun execute(command: LogoutCommand) {
        if (!tokenProvider.validateRefreshToken(command.refreshToken)) {
            throw InvalidTokenException("Invalid refresh token.")
        }

        val userId = tokenProvider.extractUserId(command.refreshToken)
        val hashedToken = tokenProvider.hashRefreshToken(command.refreshToken)

        refreshTokenRepository.deleteByUserIdAndHashedToken(userId, hashedToken)
    }
}
