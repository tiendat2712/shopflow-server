package org.shopflow.backend.domain.model.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.event.product.ProductArchivedEvent
import org.shopflow.backend.domain.event.product.ProductCreatedEvent
import org.shopflow.backend.domain.event.product.ProductMarkedOutOfStockEvent
import org.shopflow.backend.domain.event.product.ProductPublishedEvent
import org.shopflow.backend.domain.event.product.ProductRestoredEvent
import org.shopflow.backend.domain.event.product.ProductUpdatedEvent
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import java.math.BigDecimal
import java.time.Instant

class ProductDomainLifecycleTest {
    private val categoryId = CategoryId.generate()
    private val baseTime = Instant.parse("2026-11-16T10:00:00Z")

    @Test
    fun `valid lifecycle transitions update state and emit matching domain events`() {
        val (draft, createdEvent) =
            Product.create(
                categoryId = categoryId,
                name = "Lifecycle Desk",
                slug = Slug.of("lifecycle-desk"),
                sku = Sku.of("LC-DESK-1"),
                price = Money.of(BigDecimal("199.00")),
                stockQuantity = 15,
                status = ProductStatus.DRAFT,
                now = baseTime,
            )
        assertEquals(ProductCreatedEvent::class, createdEvent::class)
        assertEquals(ProductStatus.DRAFT, draft.status)

        val (published, publishedEvent) = draft.publish(expectedVersion = 0L, now = baseTime.plusSeconds(10))
        assertEquals(ProductStatus.ACTIVE, published.status)
        assertNotNull(publishedEvent)
        assertEquals(ProductPublishedEvent::class, publishedEvent!!::class)
        assertEquals(ProductStatus.DRAFT, publishedEvent.previousStatus)

        val (outOfStock, oosEvent) = published.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        assertEquals(ProductStatus.OUT_OF_STOCK, outOfStock.status)
        assertEquals(ProductMarkedOutOfStockEvent::class, oosEvent!!::class)

        val (republished, republishEvent) = outOfStock.publish(expectedVersion = 0L, now = baseTime.plusSeconds(30))
        assertEquals(ProductStatus.ACTIVE, republished.status)
        assertEquals(ProductStatus.OUT_OF_STOCK, republishEvent!!.previousStatus)

        val (archived, archivedEvent) = republished.archive(expectedVersion = 0L, now = baseTime.plusSeconds(40))
        assertEquals(ProductStatus.ARCHIVED, archived.status)
        assertEquals(ProductArchivedEvent::class, archivedEvent!!::class)

        val (restored, restoredEvent) = archived.restore(expectedVersion = 0L, now = baseTime.plusSeconds(50))
        assertEquals(ProductStatus.DRAFT, restored.status)
        assertEquals(ProductRestoredEvent::class, restoredEvent::class)
        assertEquals(ProductStatus.DRAFT, restoredEvent.restoredStatus)

        // DRAFT -> ARCHIVED directly is also valid
        val (archivedFromDraft, archivedDraftEvent) = draft.archive(expectedVersion = 0L, now = baseTime.plusSeconds(60))
        assertEquals(ProductStatus.ARCHIVED, archivedFromDraft.status)
        assertNotNull(archivedDraftEvent)
    }

    @Test
    fun `idempotent transitions return null event without state change and still enforce version`() {
        val (active, _) =
            Product.create(
                categoryId = categoryId,
                name = "Active Lamp",
                slug = Slug.of("active-lamp"),
                sku = Sku.of("LC-LAMP-1"),
                price = Money.of(BigDecimal("49.00")),
                stockQuantity = 8,
                status = ProductStatus.ACTIVE,
                now = baseTime,
            )

        val (sameActive, publishNoOpEvent) = active.publish(expectedVersion = 0L, now = baseTime.plusSeconds(5))
        assertSame(active, sameActive)
        assertNull(publishNoOpEvent)

        val (oos, _) = active.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(10))
        val (sameOos, oosNoOpEvent) = oos.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(15))
        assertSame(oos, sameOos)
        assertNull(oosNoOpEvent)

        val (archived, _) = active.archive(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        val (sameArchived, reArchiveEvent) = archived.archive(expectedVersion = 0L, now = baseTime.plusSeconds(25))
        assertSame(archived, sameArchived)
        assertNull(reArchiveEvent)

        assertThrows(ProductConcurrencyConflictException::class.java) {
            archived.archive(expectedVersion = 1L, now = baseTime.plusSeconds(30))
        }
    }

    @Test
    fun `invalid state transitions throw DomainValidationException`() {
        val (draft, _) =
            Product.create(
                categoryId = categoryId,
                name = "Transition Guard",
                slug = Slug.of("transition-guard"),
                sku = Sku.of("LC-GUARD-1"),
                price = Money.of(BigDecimal("10.00")),
                status = ProductStatus.DRAFT,
                now = baseTime,
            )
        val (active, _) = draft.publish(expectedVersion = 0L, now = baseTime.plusSeconds(5))
        val (oos, _) = active.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(10))
        val (archived, _) = active.archive(expectedVersion = 0L, now = baseTime.plusSeconds(15))

        assertTrue(ProductStatus.DRAFT.canTransitionTo(ProductStatus.ACTIVE))
        assertTrue(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.DRAFT))

        assertThrows(DomainValidationException::class.java) {
            draft.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        }
        assertThrows(DomainValidationException::class.java) {
            archived.publish(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        }
        assertThrows(DomainValidationException::class.java) {
            archived.markOutOfStock(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        }
        for (nonArchived in listOf(draft, active, oos)) {
            assertThrows(DomainValidationException::class.java) {
                nonArchived.restore(expectedVersion = 0L, now = baseTime.plusSeconds(20))
            }
        }
    }

    @Test
    fun `updateCatalogDetails preserves non-archived status, blocks archived updates, and skips no-op events`() {
        val (draft, _) =
            Product.create(
                categoryId = categoryId,
                name = "Catalog Update Guard",
                slug = Slug.of("catalog-update-guard"),
                sku = Sku.of("LC-UPD-1"),
                price = Money.of(BigDecimal("25.00")),
                stockQuantity = 3,
                status = ProductStatus.DRAFT,
                now = baseTime,
            )

        val (noChange, noOpEvent) =
            draft.updateCatalogDetails(
                categoryId = categoryId,
                name = "  Catalog Update Guard ",
                slug = draft.slug,
                sku = draft.sku,
                description = "   ",
                price = Money.of(BigDecimal("25.00")),
                stockQuantity = 3,
                imageUrl = null,
                expectedVersion = 0L,
                now = baseTime.plusSeconds(5),
            )
        assertSame(draft, noChange)
        assertNull(noOpEvent)

        val (updatedDraft, updatedEvent) =
            draft.updateCatalogDetails(
                categoryId = categoryId,
                name = "Catalog Update Guard V2",
                slug = Slug.of("catalog-update-guard-v2"),
                sku = draft.sku,
                description = "Revised copy",
                price = Money.of(BigDecimal("30.00")),
                stockQuantity = 7,
                imageUrl = "https://cdn.example/v2.png",
                expectedVersion = 0L,
                now = baseTime.plusSeconds(10),
            )
        assertEquals(ProductStatus.DRAFT, updatedDraft.status)
        assertEquals(ProductUpdatedEvent::class, updatedEvent!!::class)
        assertEquals(ProductStatus.DRAFT, updatedEvent.status)

        val (archived, _) = updatedDraft.archive(expectedVersion = 0L, now = baseTime.plusSeconds(20))
        assertThrows(DomainValidationException::class.java) {
            archived.updateCatalogDetails(
                categoryId = categoryId,
                name = "Should Fail",
                slug = archived.slug,
                sku = archived.sku,
                description = null,
                price = Money.of(BigDecimal("15.00")),
                stockQuantity = 1,
                imageUrl = null,
                expectedVersion = 0L,
                now = baseTime.plusSeconds(30),
            )
        }
    }

    @Test
    fun `rehydrate restores existing archived state, review count, and version`() {
        val persistedId = ProductId.generate()
        val updatedTime = baseTime.plusSeconds(120)
        val rehydrated =
            Product.rehydrate(
                id = persistedId,
                categoryId = categoryId,
                name = "Legacy Archived Item",
                slug = Slug.of("legacy-archived-item"),
                sku = Sku.of("LEG-ARC-1"),
                description = "Imported description",
                price = Money.of(BigDecimal("75.50")),
                stockQuantity = 0,
                averageRating = RatingAverage.of(BigDecimal("4.25")),
                reviewCount = 12,
                imageUrl = "https://cdn.example/legacy.png",
                status = ProductStatus.ARCHIVED,
                version = 7L,
                createdAt = baseTime,
                updatedAt = updatedTime,
            )

        assertEquals(persistedId, rehydrated.id)
        assertEquals(ProductStatus.ARCHIVED, rehydrated.status)
        assertEquals(12, rehydrated.reviewCount)
        assertEquals(7L, rehydrated.version)
        assertEquals(baseTime, rehydrated.createdAt)
        assertEquals(updatedTime, rehydrated.updatedAt)
    }
}
