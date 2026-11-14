package org.shopflow.backend.infrastructure.persistence.jpa.auth

import org.shopflow.backend.infrastructure.persistence.entity.auth.UserJpaEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserJpaRepository : JpaRepository<UserJpaEntity, String> {
    @EntityGraph(attributePaths = ["roles", "roles.permissions"])
    fun findByEmail(email: String): UserJpaEntity?

    @EntityGraph(attributePaths = ["roles", "roles.permissions"])
    override fun findById(id: String): Optional<UserJpaEntity>

    fun existsByEmail(email: String): Boolean

    @Query("SELECT LOWER(u.email) FROM UserJpaEntity u WHERE LOWER(u.email) IN :emails")
    fun findExistingEmails(
        @Param("emails") emails: Collection<String>,
    ): Set<String>

    @Query(value = "SELECT id FROM users WHERE id IN (:ids)", nativeQuery = true)
    fun findExistingIds(
        @Param("ids") ids: Collection<String>,
    ): Set<String>
}
