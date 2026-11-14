package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.command.auth.UpdateUserProfileCommand
import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.application.dto.auth.toProfileDto
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.exception.UserNotFoundException
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateUserProfileUseCase(
    private val userRepository: UserRepository,
    private val timeProvider: TimeProvider,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun execute(command: UpdateUserProfileCommand): UserProfileDto {
        val userId = UserId.fromString(command.userId)
        val existingUser =
            userRepository.findById(userId)
                ?: throw UserNotFoundException("User not found.")

        val (updatedUser, updatedEvent) =
            existingUser.updateProfile(
                fullName = command.fullName,
                phoneNumber = command.phoneNumber,
                city = command.city,
                address = command.address,
                avatarUrl = command.avatarUrl,
                now = timeProvider.now(),
            )

        val savedUser = userRepository.save(updatedUser)
        domainEventPublisher.publish(updatedEvent)

        return savedUser.toProfileDto()
    }
}
