package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.command.auth.LoginCommand
import org.shopflow.backend.application.dto.auth.AuthResult
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.event.auth.UserLoggedInEvent
import org.shopflow.backend.domain.exception.InvalidCredentialsException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LoginUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val authTokenIssuer: AuthTokenIssuer,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: LoginCommand): AuthResult {
        val email = Email.of(command.email)
        val user =
            userRepository.findByEmail(email)
                ?: throw InvalidCredentialsException()

        if (!passwordHasher.matches(command.rawPassword, user.hashedPassword)) {
            throw InvalidCredentialsException()
        }

        user.ensureCanAuthenticate()

        val tokens = authTokenIssuer.issueAndStoreTokens(user)
        domainEventPublisher.publish(
            UserLoggedInEvent(
                userId = user.id,
                email = user.email,
                occurredAt = timeProvider.now(),
            ),
        )
        return tokens
    }
}
