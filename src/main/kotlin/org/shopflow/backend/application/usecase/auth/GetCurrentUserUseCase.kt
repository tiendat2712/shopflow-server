package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.application.query.auth.GetCurrentUserQuery
import org.shopflow.backend.application.query.auth.UserQueryService
import org.shopflow.backend.domain.exception.UserNotFoundException
import org.shopflow.backend.domain.model.auth.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetCurrentUserUseCase(
    private val userQueryService: UserQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: GetCurrentUserQuery): UserProfileDto {
        val userId = UserId.fromString(query.userId)
        return userQueryService.findUserProfileById(userId)
            ?: throw UserNotFoundException("User not found.")
    }
}
