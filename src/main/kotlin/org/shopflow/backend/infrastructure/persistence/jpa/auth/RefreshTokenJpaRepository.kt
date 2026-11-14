package org.shopflow.backend.infrastructure.persistence.jpa.auth

import org.shopflow.backend.infrastructure.persistence.entity.auth.RefreshTokenJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, String> {
    fun findByUserIdAndHashedToken(
        userId: String,
        hashedToken: String,
    ): RefreshTokenJpaEntity?

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.userId = :userId AND r.hashedToken = :hashedToken")
    fun deleteByUserIdAndHashedToken(
        @Param("userId") userId: String,
        @Param("hashedToken") hashedToken: String,
    )

    @Modifying
    @Query("DELETE FROM RefreshTokenJpaEntity r WHERE r.userId = :userId")
    fun deleteAllByUserId(
        @Param("userId") userId: String,
    )
}
