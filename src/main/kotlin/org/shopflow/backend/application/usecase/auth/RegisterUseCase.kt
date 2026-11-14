package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.command.auth.RegisterCommand
import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.application.dto.auth.toProfileDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegisterUseCase(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordHasher: PasswordHasher,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: RegisterCommand): UserProfileDto {
        val email = Email.of(command.email)
        if (userRepository.existsByEmail(email)) {
            throw UserAlreadyExistsException()
        }

        val defaultRole =
            roleRepository.findByName(Role.ROLE_CUSTOMER)
                ?: throw RoleNotFoundException("Default role '${Role.ROLE_CUSTOMER}' is not initialized.")

        val hashedPassword = passwordHasher.encode(command.rawPassword)
        val (newUser, registeredEvent) =
            User.register(
                email = email,
                hashedPassword = hashedPassword,
                defaultRole = defaultRole,
                now = timeProvider.now(),
                fullName = command.fullName,
                city = command.city,
            )

        val savedUser = userRepository.save(newUser)
        domainEventPublisher.publish(registeredEvent)

        return savedUser.toProfileDto()
    }
}
