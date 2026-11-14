package org.shopflow.backend.infrastructure.persistence.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity

@Entity
@Table(
    name = "roles",
    indexes = [
        Index(name = "idx_roles_name", columnList = "name", unique = true),
    ],
)
class RoleJpaEntity(
    id: String,
    @field:Column(name = "name", nullable = false, unique = true, length = 50)
    var name: String,
    @field:Column(name = "description", nullable = false, length = 255)
    var description: String,
    @field:ManyToMany(fetch = FetchType.EAGER)
    @field:JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id", referencedColumnName = "id")],
    )
    var permissions: MutableSet<PermissionJpaEntity> = mutableSetOf(),
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoleJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
