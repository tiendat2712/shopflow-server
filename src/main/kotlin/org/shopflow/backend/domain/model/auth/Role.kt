package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException

data class Role(
    val id: RoleId,
    val name: String,
    val description: String,
    val permissions: Set<Permission> = emptySet(),
) {
    init {
        if (name.isBlank()) {
            throw DomainValidationException("Role name cannot be blank.")
        }
    }

    fun hasPermission(permissionCode: String): Boolean = permissions.any { it.code == permissionCode }

    fun getPermissionCodes(): Set<String> = permissions.mapTo(linkedSetOf()) { it.code }

    fun grantPermission(permission: Permission): Role = copy(permissions = permissions + permission)

    fun revokePermission(permissionCode: String): Role = copy(permissions = permissions.filterNot { it.code == permissionCode }.toSet())

    companion object {
        const val ROLE_CUSTOMER = "ROLE_CUSTOMER"
        const val ROLE_STAFF = "ROLE_STAFF"
        const val ROLE_ADMIN = "ROLE_ADMIN"

        fun create(
            name: String,
            description: String,
            permissions: Set<Permission> = emptySet(),
        ): Role =
            Role(
                id = RoleId.generate(),
                name = name.trim().uppercase(),
                description = description.trim(),
                permissions = permissions,
            )
    }
}
