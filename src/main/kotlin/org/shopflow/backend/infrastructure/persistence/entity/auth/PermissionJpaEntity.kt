package org.shopflow.backend.infrastructure.persistence.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity

@Entity
@Table(
    name = "permissions",
    indexes = [
        Index(name = "idx_permissions_code", columnList = "code", unique = true),
        Index(name = "idx_permissions_module", columnList = "module"),
    ],
)
class PermissionJpaEntity(
    id: String,
    @field:Column(name = "code", nullable = false, unique = true, length = 100)
    var code: String,
    @field:Column(name = "description", nullable = false, length = 255)
    var description: String,
    @field:Column(name = "module", nullable = false, length = 50)
    var module: String,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PermissionJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
