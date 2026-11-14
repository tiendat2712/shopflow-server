package org.shopflow.backend.domain.repository.auth

import org.shopflow.backend.domain.model.auth.Permission

interface PermissionRepository {
    fun findByCode(code: String): Permission?

    fun findAllByCodes(codes: Collection<String>): List<Permission>

    fun findAll(): List<Permission>

    fun save(permission: Permission): Permission

    fun saveAll(permissions: Collection<Permission>): List<Permission>
}
