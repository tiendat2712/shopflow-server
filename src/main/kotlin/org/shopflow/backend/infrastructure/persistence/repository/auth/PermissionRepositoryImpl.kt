package org.shopflow.backend.infrastructure.persistence.repository.auth

import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.repository.auth.PermissionRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.PermissionJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.auth.PermissionPersistenceMapper
import org.springframework.stereotype.Repository

@Repository
class PermissionRepositoryImpl(
    private val jpaRepository: PermissionJpaRepository,
    private val mapper: PermissionPersistenceMapper,
) : PermissionRepository {
    override fun findByCode(code: String): Permission? = jpaRepository.findByCode(code)?.let(mapper::toDomain)

    override fun findAllByCodes(codes: Collection<String>): List<Permission> {
        if (codes.isEmpty()) return emptyList()
        return jpaRepository.findAllByCodeIn(codes).map(mapper::toDomain)
    }

    override fun findAll(): List<Permission> = jpaRepository.findAll().map(mapper::toDomain)

    override fun save(permission: Permission): Permission {
        val existing = jpaRepository.findById(permission.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    code = permission.code
                    description = permission.description
                    module = permission.module
                }
            } else {
                mapper.toEntity(permission)
            }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity))
    }

    override fun saveAll(permissions: Collection<Permission>): List<Permission> {
        if (permissions.isEmpty()) return emptyList()
        val existingById =
            jpaRepository
                .findAllById(permissions.map { it.id.value.toString() })
                .associateBy { it.getId() }
        val entities =
            permissions.map { domain ->
                existingById[domain.id.value.toString()]?.apply {
                    code = domain.code
                    description = domain.description
                    module = domain.module
                } ?: mapper.toEntity(domain)
            }
        return jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
    }
}
