package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException
import java.util.UUID

@JvmInline
value class PermissionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun generate(): PermissionId = PermissionId(UUID.randomUUID())

        fun fromString(raw: String): PermissionId =
            try {
                PermissionId(UUID.fromString(raw))
            } catch (ex: IllegalArgumentException) {
                throw DomainValidationException("Invalid permission id.")
            }
    }
}
