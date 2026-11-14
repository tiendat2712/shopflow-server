package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException

data class Permission(
    val id: PermissionId,
    val code: String,
    val description: String,
    val module: String,
) {
    init {
        if (code.isBlank()) {
            throw DomainValidationException("Permission code cannot be blank.")
        }
        if (module.isBlank()) {
            throw DomainValidationException("Permission module cannot be blank.")
        }
    }

    companion object {
        const val USER_READ = "USER_READ"
        const val USER_MANAGE = "USER_MANAGE"
        const val PRODUCT_READ = "PRODUCT_READ"
        const val PRODUCT_WRITE = "PRODUCT_WRITE"
        const val ORDER_CREATE = "ORDER_CREATE"
        const val ORDER_READ_OWN = "ORDER_READ_OWN"
        const val ORDER_MANAGE_ALL = "ORDER_MANAGE_ALL"
        const val PAYMENT_VERIFY = "PAYMENT_VERIFY"
        const val FLASH_SALE_MANAGE = "FLASH_SALE_MANAGE"

        fun create(
            code: String,
            description: String,
            module: String,
        ): Permission =
            Permission(
                id = PermissionId.generate(),
                code = code.trim().uppercase(),
                description = description.trim(),
                module = module.trim().uppercase(),
            )
    }
}
