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
        fun generate(): ProductId = ProductId(UUID.randomUUID())

        fun deterministic(key: String): ProductId = ProductId(DatasetUuidConverter.toProductUuid(key))

        fun fromString(raw: String): ProductId =
            try {
                ProductId(UUID.fromString(raw.trim()))
            } catch (ex: IllegalArgumentException) {
                throw DomainValidationException("Invalid product ID format.")
            }
    }
}
