package org.shopflow.backend.domain.repository.auth

import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.RoleId

interface RoleRepository {
    fun findByName(name: String): Role?

    fun findById(id: RoleId): Role?

    fun findAll(): List<Role>

    fun save(role: Role): Role
}
