package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import java.util.UUID

@JvmInline
value class ProductId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        private val CANONICAL_UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun generate(): ProductId = ProductId(UUID.randomUUID())

        fun deterministic(key: String): ProductId = ProductId(DatasetUuidConverter.toProductUuid(key))

        fun fromString(raw: String): ProductId {
            val trimmed = raw.trim()
            if (!CANONICAL_UUID_REGEX.matches(trimmed)) {
                throw DomainValidationException("Invalid product ID format.")
            }
            return try {
                ProductId(UUID.fromString(trimmed))
            } catch (_: IllegalArgumentException) {
                throw DomainValidationException("Invalid product ID format.")
            }
        }
    }
}
