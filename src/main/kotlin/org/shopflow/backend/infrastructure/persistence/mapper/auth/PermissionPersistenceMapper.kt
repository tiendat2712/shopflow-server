package org.shopflow.backend.infrastructure.persistence.mapper.auth

import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.PermissionId
import org.shopflow.backend.infrastructure.persistence.entity.auth.PermissionJpaEntity
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PermissionPersistenceMapper {
    fun toDomain(entity: PermissionJpaEntity): Permission =
        Permission(
            id = PermissionId(UUID.fromString(entity.getId())),
            code = entity.code,
            description = entity.description,
            module = entity.module,
        )

    fun toEntity(domain: Permission): PermissionJpaEntity =
        PermissionJpaEntity(
            id = domain.id.value.toString(),
            code = domain.code,
            description = domain.description,
            module = domain.module,
        )
}
