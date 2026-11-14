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
        fun generate(): CategoryId = CategoryId(UUID.randomUUID())

        fun deterministic(key: String): CategoryId = CategoryId(DatasetUuidConverter.toCategoryUuid(key))

        fun fromString(raw: String): CategoryId =
            try {
                CategoryId(UUID.fromString(raw.trim()))
            } catch (ex: IllegalArgumentException) {
                throw DomainValidationException("Invalid category ID format.")
            }
    }
}
