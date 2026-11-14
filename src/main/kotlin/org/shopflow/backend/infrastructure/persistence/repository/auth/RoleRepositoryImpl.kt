package org.shopflow.backend.infrastructure.persistence.repository.auth

import org.shopflow.backend.domain.exception.PermissionNotFoundException
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.RoleId
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.infrastructure.persistence.entity.auth.PermissionJpaEntity
import org.shopflow.backend.infrastructure.persistence.jpa.auth.PermissionJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RoleJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.auth.RolePersistenceMapper
import org.springframework.stereotype.Repository

@Repository
class RoleRepositoryImpl(
    private val jpaRepository: RoleJpaRepository,
    private val permissionJpaRepository: PermissionJpaRepository,
    private val mapper: RolePersistenceMapper,
) : RoleRepository {
    override fun findByName(name: String): Role? = jpaRepository.findByName(name)?.let(mapper::toDomain)

    override fun findById(id: RoleId): Role? = jpaRepository.findById(id.value.toString()).orElse(null)?.let(mapper::toDomain)

    override fun findAll(): List<Role> = jpaRepository.findAll().map(mapper::toDomain)

    override fun save(role: Role): Role {
        val resolvedPermissions = resolvePermissions(role.permissions)
        val existing = jpaRepository.findById(role.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    name = role.name
                    description = role.description
                    permissions.clear()
                    permissions.addAll(resolvedPermissions)
                }
            } else {
                mapper.toEntity(role, resolvedPermissions)
            }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity))
    }

    private fun resolvePermissions(permissions: Set<Permission>): MutableSet<PermissionJpaEntity> {
        if (permissions.isEmpty()) return mutableSetOf()
        val requestedIds = permissions.map { it.id.value.toString() }
        val foundById = permissionJpaRepository.findAllById(requestedIds).associateBy { it.getId() }
        val resolved = linkedSetOf<PermissionJpaEntity>()
        for (permission in permissions) {
            val found =
                foundById[permission.id.value.toString()]
                    ?: throw PermissionNotFoundException("Permission '${permission.code}' not found.")
            resolved.add(found)
        }
        return resolved
    }
}
