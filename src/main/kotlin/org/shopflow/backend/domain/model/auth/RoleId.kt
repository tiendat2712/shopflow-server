package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException
import java.util.UUID

@JvmInline
value class RoleId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun generate(): RoleId = RoleId(UUID.randomUUID())

        fun fromString(raw: String): RoleId =
            try {
                RoleId(UUID.fromString(raw))
            } catch (ex: IllegalArgumentException) {
                throw DomainValidationException("Invalid role id.")
            }
    }
}
