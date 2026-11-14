package org.shopflow.backend.infrastructure.config

import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.repository.auth.PermissionRepository
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
@Order(100)
class RbacDataSeeder(
    private val jdbcTemplate: JdbcTemplate,
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val transactionTemplate: TransactionTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        dropLegacyUserRoleColumnIfPresent()
        transactionTemplate.executeWithoutResult {
            seedDefaultPermissionsAndRoles()
        }
    }

    private fun dropLegacyUserRoleColumnIfPresent() {
        val legacyColumnCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'users'
                  AND column_name = 'role'
                """.trimIndent(),
                Int::class.java,
            ) ?: 0

        if (legacyColumnCount > 0) {
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN role")
        }
    }

    private fun seedDefaultPermissionsAndRoles() {
        val existingPermissions =
            permissionRepository
                .findAll()
                .associateByTo(linkedMapOf()) { it.code }

        val toCreate =
            DEFAULT_PERMISSIONS
                .filter { spec -> spec.code !in existingPermissions }
                .map { spec ->
                    Permission.create(
                        code = spec.code,
                        description = spec.description,
                        module = spec.module,
                    )
                }

        if (toCreate.isNotEmpty()) {
            permissionRepository.saveAll(toCreate).forEach { saved ->
                existingPermissions[saved.code] = saved
            }
        }

        DEFAULT_ROLES.forEach { roleSpec ->
            if (roleRepository.findByName(roleSpec.name) == null) {
                val rolePermissions =
                    roleSpec.permissionCodes
                        .mapNotNullTo(linkedSetOf()) { code -> existingPermissions[code] }

                roleRepository.save(
                    Role.create(
                        name = roleSpec.name,
                        description = roleSpec.description,
                        permissions = rolePermissions,
                    ),
                )
            }
        }
    }

    private data class PermissionSeedSpec(
        val code: String,
        val description: String,
        val module: String,
    )

    private data class RoleSeedSpec(
        val name: String,
        val description: String,
        val permissionCodes: Set<String>,
    )

    companion object {
        private val DEFAULT_PERMISSIONS =
            listOf(
                PermissionSeedSpec(Permission.USER_READ, "View own user profile", "USER"),
                PermissionSeedSpec(Permission.USER_MANAGE, "Manage user accounts and roles", "USER"),
                PermissionSeedSpec(Permission.PRODUCT_READ, "Browse and view products", "PRODUCT"),
                PermissionSeedSpec(Permission.PRODUCT_WRITE, "Create, update, and delete products", "PRODUCT"),
                PermissionSeedSpec(Permission.ORDER_CREATE, "Place new customer orders", "ORDER"),
                PermissionSeedSpec(Permission.ORDER_READ_OWN, "View own order history", "ORDER"),
                PermissionSeedSpec(Permission.ORDER_MANAGE_ALL, "Manage and fulfill all orders", "ORDER"),
                PermissionSeedSpec(Permission.PAYMENT_VERIFY, "Verify and reconcile payments", "PAYMENT"),
                PermissionSeedSpec(Permission.FLASH_SALE_MANAGE, "Configure and manage flash sales", "PROMOTION"),
            )

        private val DEFAULT_ROLES =
            listOf(
                RoleSeedSpec(
                    name = Role.ROLE_CUSTOMER,
                    description = "Default customer role",
                    permissionCodes =
                        setOf(
                            Permission.USER_READ,
                            Permission.PRODUCT_READ,
                            Permission.ORDER_CREATE,
                            Permission.ORDER_READ_OWN,
                        ),
                ),
                RoleSeedSpec(
                    name = Role.ROLE_STAFF,
                    description = "Store operations staff role",
                    permissionCodes =
                        setOf(
                            Permission.USER_READ,
                            Permission.PRODUCT_READ,
                            Permission.PRODUCT_WRITE,
                            Permission.ORDER_READ_OWN,
                            Permission.ORDER_MANAGE_ALL,
                            Permission.PAYMENT_VERIFY,
                        ),
                ),
                RoleSeedSpec(
                    name = Role.ROLE_ADMIN,
                    description = "System administrator with full permissions",
                    permissionCodes = DEFAULT_PERMISSIONS.mapTo(linkedSetOf()) { it.code },
                ),
            )
    }
}
