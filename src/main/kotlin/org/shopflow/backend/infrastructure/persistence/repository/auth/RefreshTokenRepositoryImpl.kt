package org.shopflow.backend.infrastructure.persistence.repository.auth

import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RefreshTokenJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.auth.RefreshTokenPersistenceMapper
import org.springframework.stereotype.Repository

@Repository
class RefreshTokenRepositoryImpl(
    private val jpaRepository: RefreshTokenJpaRepository,
    private val mapper: RefreshTokenPersistenceMapper,
) : RefreshTokenRepository {
    override fun findByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): RefreshToken? =
        jpaRepository
            .findByUserIdAndHashedToken(userId.value.toString(), hashedToken)
            ?.let(mapper::toDomain)

    override fun deleteByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ) {
        jpaRepository.deleteByUserIdAndHashedToken(userId.value.toString(), hashedToken)
    }

    override fun deleteAllByUserId(userId: UserId) {
        jpaRepository.deleteAllByUserId(userId.value.toString())
    }

    override fun save(refreshToken: RefreshToken): RefreshToken {
        val existing = jpaRepository.findById(refreshToken.id.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    userId = refreshToken.userId.value.toString()
                    hashedToken = refreshToken.hashedToken
                    expiresAt = refreshToken.expiresAt
                }
            } else {
                mapper.toEntity(refreshToken)
            }
        val saved = jpaRepository.saveAndFlush(entity)
        return mapper.toDomain(saved)
    }
}
