package org.shopflow.backend.infrastructure.persistence.jpa.auth

import org.shopflow.backend.infrastructure.persistence.entity.auth.PermissionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PermissionJpaRepository : JpaRepository<PermissionJpaEntity, String> {
    fun findByCode(code: String): PermissionJpaEntity?

    fun findAllByCodeIn(codes: Collection<String>): List<PermissionJpaEntity>
}
