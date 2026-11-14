package org.shopflow.backend.infrastructure.persistence.mapper.auth

import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.infrastructure.persistence.entity.auth.RoleJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.auth.UserJpaEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class UserPersistenceMapper(
    private val roleMapper: RolePersistenceMapper,
) {
    fun toDomain(entity: UserJpaEntity): User =
        User(
            id = UserId(UUID.fromString(entity.getId())),
            email = Email.of(entity.email),
            hashedPassword = HashedPassword(entity.hashedPassword),
            roles = entity.roles.mapTo(linkedSetOf(), roleMapper::toDomain),
            status = entity.status,
            createdAt = entity.createdAt ?: Instant.EPOCH,
            updatedAt = entity.updatedAt ?: Instant.EPOCH,
            fullName = entity.fullName,
            phoneNumber = entity.phoneNumber,
            city = entity.city,
            address = entity.address,
            avatarUrl = entity.avatarUrl,
        )

    fun toEntity(
        domain: User,
        resolvedRoles: MutableSet<RoleJpaEntity>,
    ): UserJpaEntity =
        UserJpaEntity(
            id = domain.id.value.toString(),
            email = domain.email.value,
            hashedPassword = domain.hashedPassword.value,
            roles = resolvedRoles,
            status = domain.status,
            fullName = domain.fullName,
            phoneNumber = domain.phoneNumber,
            city = domain.city,
            address = domain.address,
            avatarUrl = domain.avatarUrl,
        )
}
