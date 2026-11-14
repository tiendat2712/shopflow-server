package org.shopflow.backend.domain.service.dataset

import org.shopflow.backend.domain.exception.DomainValidationException
import java.util.UUID

/**
 * Converts raw identifiers from CSV datasets into canonical 36-character UUIDs,
 * preserving existing canonical UUID values and hashing non-UUID keys deterministically.
 */
object DatasetUuidConverter {
    private val CANONICAL_UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun toUserUuid(rawId: String): UUID = convert("user", rawId)

    fun userId(rawId: String): String = toUserUuid(rawId).toString()

    fun toCategoryUuid(categoryName: String): UUID = convert("category", categoryName, lowercaseKey = true)

    fun categoryId(categoryName: String): String = toCategoryUuid(categoryName).toString()

    fun toProductUuid(rawId: String): UUID = convert("product", rawId)

    fun productId(rawId: String): String = toProductUuid(rawId).toString()

    fun toOrderUuid(rawId: String): UUID = convert("order", rawId)

    fun orderId(rawId: String): String = toOrderUuid(rawId).toString()

    fun toOrderItemUuid(rawId: String): UUID = convert("order-item", rawId)

    fun orderItemId(rawId: String): String = toOrderItemUuid(rawId).toString()

    fun toReviewUuid(rawId: String): UUID = convert("review", rawId)

    fun reviewId(rawId: String): String = toReviewUuid(rawId).toString()

    fun toEventUuid(rawId: String): UUID = convert("event", rawId)

    fun eventId(rawId: String): String = toEventUuid(rawId).toString()

    private fun convert(
        namespace: String,
        rawValue: String,
        lowercaseKey: Boolean = false,
    ): UUID {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            throw DomainValidationException("Dataset identifier cannot be blank.")
        }
        if (CANONICAL_UUID_REGEX.matches(trimmed)) {
            return UUID.fromString(trimmed)
        }
        val key = if (lowercaseKey) trimmed.lowercase() else trimmed
        return UUID.nameUUIDFromBytes("shopflow:$namespace:$key".toByteArray(Charsets.UTF_8))
    }
}
