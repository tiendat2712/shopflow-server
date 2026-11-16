package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import java.util.UUID

@JvmInline
value class CategoryId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        private val CANONICAL_UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun generate(): CategoryId = CategoryId(UUID.randomUUID())

        fun deterministic(key: String): CategoryId = CategoryId(DatasetUuidConverter.toCategoryUuid(key))

        fun fromString(raw: String): CategoryId {
            val trimmed = raw.trim()
            if (!CANONICAL_UUID_REGEX.matches(trimmed)) {
                throw DomainValidationException("Invalid category ID format.")
            }
            return try {
                CategoryId(UUID.fromString(trimmed))
            } catch (_: IllegalArgumentException) {
                throw DomainValidationException("Invalid category ID format.")
            }
        }
    }
}
