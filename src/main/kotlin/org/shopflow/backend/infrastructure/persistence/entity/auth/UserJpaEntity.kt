package org.shopflow.backend.infrastructure.persistence.entity.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_users_email", columnList = "email", unique = true),
        Index(name = "idx_users_city", columnList = "city"),
    ],
)
class UserJpaEntity(
    id: String,
    @field:Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String,
    @field:Column(name = "hashed_password", nullable = false, length = 255)
    var hashedPassword: String,
    @field:ManyToMany(fetch = FetchType.EAGER)
    @field:JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "role_id", referencedColumnName = "id")],
    )
    var roles: MutableSet<RoleJpaEntity> = mutableSetOf(),
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 32)
    var status: UserStatus,
    @field:Column(name = "full_name", length = 150)
    var fullName: String? = null,
    @field:Column(name = "phone_number", length = 30)
    var phoneNumber: String? = null,
    @field:Column(name = "city", length = 100)
    var city: String? = null,
    @field:Column(name = "address", length = 500)
    var address: String? = null,
    @field:Column(name = "avatar_url", length = 500)
    var avatarUrl: String? = null,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
