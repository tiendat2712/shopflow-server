package org.shopflow.backend.infrastructure.persistence.repository.auth

import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RefreshTokenJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.auth.RefreshTokenPersistenceMapper
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Repository
class RefreshTokenRepositoryImpl(
    private val jpaRepository: RefreshTokenJpaRepository,
    private val mapper: RefreshTokenPersistenceMapper,
    transactionManager: PlatformTransactionManager,
) : RefreshTokenRepository {
    private val requiresNewTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    override fun findByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): RefreshToken? =
        jpaRepository
            .findByUserIdAndHashedToken(userId.value.toString(), hashedToken)
            ?.let(mapper::toDomain)

    override fun consumeIfValid(
        userId: UserId,
        hashedToken: String,
        now: Instant,
    ): Boolean =
        try {
            jpaRepository.deleteIfValid(
                userId = userId.value.toString(),
                hashedToken = hashedToken,
                now = now,
            ) > 0
        } catch (_: PessimisticLockingFailureException) {
            throw InvalidTokenException("Refresh token is no longer valid or is being rotated.")
        }

    override fun deleteByUserIdAndHashedToken(
        userId: UserId,
        hashedToken: String,
    ): Boolean {
        val rawUserId = userId.value.toString()
        var lastException: PessimisticLockingFailureException? = null
        repeat(MAX_DELETE_ATTEMPTS) {
            try {
                return requiresNewTransactionTemplate.execute {
                    jpaRepository.deleteByUserIdAndHashedToken(rawUserId, hashedToken) > 0
                }
            } catch (ex: PessimisticLockingFailureException) {
                lastException = ex
            }
        }
        throw checkNotNull(lastException)
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

    companion object {
        private const val MAX_DELETE_ATTEMPTS = 3
    }
}
