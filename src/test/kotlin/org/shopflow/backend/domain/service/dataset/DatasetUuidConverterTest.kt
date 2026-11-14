package org.shopflow.backend.domain.service.dataset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.exception.DomainValidationException
import java.util.UUID

class DatasetUuidConverterTest {
    @Test
    fun `converts raw CSV identifiers deterministically using exact namespaces`() {
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:user:U000001".toByteArray(Charsets.UTF_8)),
            DatasetUuidConverter.toUserUuid("  U000001 "),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:category:home & kitchen".toByteArray(Charsets.UTF_8)).toString(),
            DatasetUuidConverter.categoryId("  Home & Kitchen  "),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:product:P000001".toByteArray(Charsets.UTF_8)),
            DatasetUuidConverter.toProductUuid("P000001"),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:order:O00000001".toByteArray(Charsets.UTF_8)).toString(),
            DatasetUuidConverter.orderId("O00000001"),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:order-item:I00000001".toByteArray(Charsets.UTF_8)).toString(),
            DatasetUuidConverter.orderItemId("I00000001"),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:review:R00000528".toByteArray(Charsets.UTF_8)).toString(),
            DatasetUuidConverter.reviewId("R00000528"),
        )
        assertEquals(
            UUID.nameUUIDFromBytes("shopflow:event:E00000001".toByteArray(Charsets.UTF_8)).toString(),
            DatasetUuidConverter.eventId("E00000001"),
        )
    }

    @Test
    fun `preserves existing canonical 36-char UUIDs across all entity converters`() {
        val lowerUuid = "550e8400-e29b-41d4-a716-446655440000"
        val upperUuid = "550E8400-E29B-41D4-A716-446655440000"

        assertEquals(lowerUuid, DatasetUuidConverter.userId(lowerUuid))
        assertEquals(lowerUuid, DatasetUuidConverter.categoryId("  $upperUuid "))
        assertEquals(lowerUuid, DatasetUuidConverter.productId(lowerUuid))
        assertEquals(lowerUuid, DatasetUuidConverter.orderId(upperUuid))
        assertEquals(lowerUuid, DatasetUuidConverter.orderItemId(lowerUuid))
        assertEquals(lowerUuid, DatasetUuidConverter.reviewId(lowerUuid))
        assertEquals(lowerUuid, DatasetUuidConverter.eventId(lowerUuid))
    }

    @Test
    fun `hashes short pseudo-UUID strings instead of treating them as canonical UUIDs`() {
        val pseudoUuid = "1-2-3-4-5"
        val converted = DatasetUuidConverter.toProductUuid(pseudoUuid)
        val expected = UUID.nameUUIDFromBytes("shopflow:product:1-2-3-4-5".toByteArray(Charsets.UTF_8))

        assertEquals(expected, converted)
        assertNotEquals(UUID.fromString(pseudoUuid), converted)
    }

    @Test
    fun `keeps foreign-key conversions consistent and rejects blank identifiers`() {
        assertEquals(DatasetUuidConverter.userId("U009310"), DatasetUuidConverter.userId(" U009310 "))
        assertEquals(DatasetUuidConverter.productId("P001758"), DatasetUuidConverter.productId("P001758"))
        assertEquals(DatasetUuidConverter.orderId("O00000001"), DatasetUuidConverter.orderId("O00000001"))
        assertNotEquals(DatasetUuidConverter.userId("1"), DatasetUuidConverter.productId("1"))

        assertThrows(DomainValidationException::class.java) {
            DatasetUuidConverter.userId("   ")
        }
    }
}
