package org.shopflow.backend.infrastructure.persistence.mapper.auth

import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.infrastructure.persistence.entity.auth.RefreshTokenJpaEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class RefreshTokenPersistenceMapper {
    fun toDomain(entity: RefreshTokenJpaEntity): RefreshToken =
        RefreshToken(
            id = UUID.fromString(entity.getId()),
            userId = UserId(UUID.fromString(entity.userId)),
            hashedToken = entity.hashedToken,
            expiresAt = entity.expiresAt,
            createdAt = entity.createdAt ?: Instant.EPOCH,
        )

    fun toEntity(domain: RefreshToken): RefreshTokenJpaEntity =
        RefreshTokenJpaEntity(
            id = domain.id.toString(),
            userId = domain.userId.value.toString(),
            hashedToken = domain.hashedToken,
            expiresAt = domain.expiresAt,
        )
}
