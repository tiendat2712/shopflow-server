package org.shopflow.backend.infrastructure.persistence.jpa.auth

import org.shopflow.backend.infrastructure.persistence.entity.auth.RoleJpaEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RoleJpaRepository : JpaRepository<RoleJpaEntity, String> {
    @EntityGraph(attributePaths = ["permissions"])
    fun findByName(name: String): RoleJpaEntity?

    @EntityGraph(attributePaths = ["permissions"])
    override fun findById(id: String): Optional<RoleJpaEntity>

    @EntityGraph(attributePaths = ["permissions"])
    override fun findAll(): List<RoleJpaEntity>
}
