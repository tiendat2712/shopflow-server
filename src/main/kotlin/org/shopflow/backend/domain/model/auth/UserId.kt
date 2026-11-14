package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import java.util.UUID

@JvmInline
value class UserId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun generate(): UserId = UserId(UUID.randomUUID())

        fun deterministic(key: String): UserId = UserId(DatasetUuidConverter.toUserUuid(key))

        fun fromString(raw: String): UserId =
            try {
                UserId(UUID.fromString(raw))
            } catch (ex: IllegalArgumentException) {
                throw InvalidTokenException("Invalid token.")
            }
    }
}
