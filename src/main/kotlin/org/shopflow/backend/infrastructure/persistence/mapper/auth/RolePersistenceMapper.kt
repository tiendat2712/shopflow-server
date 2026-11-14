package org.shopflow.backend.infrastructure.persistence.mapper.auth

import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.RoleId
import org.shopflow.backend.infrastructure.persistence.entity.auth.PermissionJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.auth.RoleJpaEntity
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RolePersistenceMapper(
    private val permissionMapper: PermissionPersistenceMapper,
) {
    fun toDomain(entity: RoleJpaEntity): Role =
        Role(
            id = RoleId(UUID.fromString(entity.getId())),
            name = entity.name,
            description = entity.description,
            permissions = entity.permissions.mapTo(linkedSetOf(), permissionMapper::toDomain),
        )

    fun toEntity(
        domain: Role,
        resolvedPermissions: MutableSet<PermissionJpaEntity>,
    ): RoleJpaEntity =
        RoleJpaEntity(
            id = domain.id.value.toString(),
            name = domain.name,
            description = domain.description,
            permissions = resolvedPermissions,
        )
}
