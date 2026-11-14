package org.shopflow.backend.infrastructure.query.auth

import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.application.query.auth.UserQueryService
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.infrastructure.persistence.jpa.auth.UserJpaRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserQueryServiceImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserQueryService {
    override fun findUserProfileById(userId: UserId): UserProfileDto? =
        userJpaRepository
            .findById(userId.value.toString())
            .orElse(null)
            ?.let { entity ->
                val roleNames = entity.roles.mapTo(linkedSetOf()) { it.name }
                val permissionCodes =
                    entity.roles.flatMapTo(linkedSetOf()) { role ->
                        role.permissions.map { it.code }
                    }
                UserProfileDto(
                    id = entity.getId(),
                    email = entity.email,
                    fullName = entity.fullName,
                    phoneNumber = entity.phoneNumber,
                    city = entity.city,
                    address = entity.address,
                    avatarUrl = entity.avatarUrl,
                    roles = roleNames,
                    permissions = permissionCodes,
                    status = entity.status.name,
                    createdAt = entity.createdAt ?: Instant.EPOCH,
                )
            }
}
