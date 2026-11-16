package org.shopflow.backend.application.usecase.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.product.CreateCategoryCommand
import org.shopflow.backend.application.command.product.CreateProductCommand
import org.shopflow.backend.application.command.product.DeleteProductCommand
import org.shopflow.backend.application.command.product.PublishProductCommand
import org.shopflow.backend.application.command.product.RestoreProductCommand
import org.shopflow.backend.application.command.product.UpdateProductCommand
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.dto.product.toDetailDto
import org.shopflow.backend.application.dto.product.toDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.CategorySortField
import org.shopflow.backend.application.query.product.GetAdminProductDetailQuery
import org.shopflow.backend.application.query.product.GetProductDetailQuery
import org.shopflow.backend.application.query.product.ListAdminCategoriesQuery
import org.shopflow.backend.application.query.product.ListAdminProductsQuery
import org.shopflow.backend.application.query.product.ListCategoriesQuery
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.application.query.product.ProductSortField
import org.shopflow.backend.application.query.product.SortDirection
import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.shopflow.backend.domain.event.product.CategoryCreatedEvent
import org.shopflow.backend.domain.event.product.ProductArchivedEvent
import org.shopflow.backend.domain.event.product.ProductCreatedEvent
import org.shopflow.backend.domain.event.product.ProductPublishedEvent
import org.shopflow.backend.domain.event.product.ProductRestoredEvent
import org.shopflow.backend.domain.event.product.ProductUpdatedEvent
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.domain.model.product.RatingAverage
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import java.math.BigDecimal
import java.time.Instant

class ProductCatalogUseCasesTest {
    private lateinit var categoryRepository: InMemoryCategoryRepository
    private lateinit var productRepository: InMemoryProductRepository
    private lateinit var eventPublisher: RecordingPublisher
    private lateinit var clock: Instant

    private lateinit var createCategoryUseCase: CreateCategoryUseCase
    private lateinit var listCategoriesUseCase: ListCategoriesUseCase
    private lateinit var listAdminCategoriesUseCase: ListAdminCategoriesUseCase
    private lateinit var createProductUseCase: CreateProductUseCase
    private lateinit var updateProductUseCase: UpdateProductUseCase
    private lateinit var deleteProductUseCase: DeleteProductUseCase
    private lateinit var publishProductUseCase: PublishProductUseCase
    private lateinit var restoreProductUseCase: RestoreProductUseCase
    private lateinit var listProductsUseCase: ListProductsUseCase
    private lateinit var listAdminProductsUseCase: ListAdminProductsUseCase
    private lateinit var getProductDetailUseCase: GetProductDetailUseCase
    private lateinit var getAdminProductDetailUseCase: GetAdminProductDetailUseCase

    @BeforeEach
    fun setUp() {
        categoryRepository = InMemoryCategoryRepository()
        productRepository = InMemoryProductRepository(categoryRepository)
        eventPublisher = RecordingPublisher()
        clock = Instant.parse("2026-11-15T00:00:00Z")
        val timeProvider = TimeProvider { clock }

        createCategoryUseCase = CreateCategoryUseCase(categoryRepository, timeProvider, eventPublisher)
        listCategoriesUseCase = ListCategoriesUseCase(categoryRepository)
        listAdminCategoriesUseCase = ListAdminCategoriesUseCase(categoryRepository)
        createProductUseCase = CreateProductUseCase(categoryRepository, productRepository, timeProvider, eventPublisher)
        updateProductUseCase = UpdateProductUseCase(categoryRepository, productRepository, timeProvider, eventPublisher)
        deleteProductUseCase = DeleteProductUseCase(productRepository, timeProvider, eventPublisher)
        publishProductUseCase = PublishProductUseCase(productRepository, categoryRepository, timeProvider, eventPublisher)
        restoreProductUseCase = RestoreProductUseCase(productRepository, categoryRepository, timeProvider, eventPublisher)
        listProductsUseCase = ListProductsUseCase(productRepository)
        listAdminProductsUseCase = ListAdminProductsUseCase(productRepository)
        getProductDetailUseCase = GetProductDetailUseCase(productRepository)
        getAdminProductDetailUseCase = GetAdminProductDetailUseCase(productRepository)
    }

    @Test
    fun `category and product lifecycle enforces invariants, publishes events, and supports CQRS queries`() {
        val category = createCategoryUseCase.execute(CreateCategoryCommand(name = "Home & Kitchen", displayOrder = 1))
        assertEquals("home-kitchen", category.slug)
        assertEquals(CategoryCreatedEvent::class, eventPublisher.events.last()::class)
        assertEquals(1L, listCategoriesUseCase.execute(ListCategoriesQuery()).totalElements)

        assertThrows(DuplicateCategorySlugException::class.java) {
            createCategoryUseCase.execute(CreateCategoryCommand(name = "Home Kitchen"))
        }

        assertThrows(DomainValidationException::class.java) {
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = category.id,
                    name = "Ceramic Coffee Dripper",
                    sku = "hk-drip-01",
                    price = BigDecimal("29.995"),
                    stockQuantity = 50,
                ),
            )
        }

        val created =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = category.id,
                    name = "Ceramic Coffee Dripper",
                    sku = "hk-drip-01",
                    price = BigDecimal("30.000"),
                    stockQuantity = 50,
                ),
            )
        assertEquals("HK-DRIP-01", created.sku)
        assertEquals("ceramic-coffee-dripper", created.slug)
        assertEquals(BigDecimal("30.00"), created.price)
        assertEquals(0L, created.version)
        assertEquals(ProductCreatedEvent::class, eventPublisher.events.last()::class)

        assertThrows(DuplicateProductSkuException::class.java) {
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = category.id,
                    name = "Another Product",
                    sku = "HK-DRIP-01",
                    price = BigDecimal("10.00"),
                ),
            )
        }

        clock = clock.plusSeconds(60)
        val updated =
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = created.id,
                    categoryId = category.id,
                    name = "Ceramic Coffee Dripper V2",
                    sku = "HK-DRIP-01",
                    price = BigDecimal("35.50"),
                    stockQuantity = 25,
                    expectedVersion = created.version,
                ),
            )
        assertEquals("Ceramic Coffee Dripper V2", updated.name)
        assertEquals(BigDecimal("35.50"), updated.price)
        assertEquals(1L, updated.version)
        assertEquals(ProductUpdatedEvent::class, eventPublisher.events.last()::class)

        val detail = getProductDetailUseCase.execute(GetProductDetailQuery(updated.slug))
        assertEquals(updated.id, detail.id)
        assertEquals(1L, detail.version)
        assertEquals("Home & Kitchen", detail.categoryName)

        val page = listProductsUseCase.execute(ListProductsQuery(keyword = "dripper", sortBy = "price_asc"))
        assertEquals(1, page.totalElements)

        deleteProductUseCase.execute(DeleteProductCommand(updated.id, updated.version))
        assertThrows(ProductNotFoundException::class.java) {
            getProductDetailUseCase.execute(GetProductDetailQuery(updated.id))
        }
        assertThrows(ProductNotFoundException::class.java) {
            getProductDetailUseCase.execute(GetProductDetailQuery(updated.slug))
        }
        val archivedDetail = getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(updated.id))
        assertEquals(ProductStatus.ARCHIVED.name, archivedDetail.status)
        assertEquals(2L, archivedDetail.version)
        assertEquals(ProductArchivedEvent::class, eventPublisher.events.last()::class)
    }

    @Test
    fun `optimistic concurrency rejects stale updates archives and bulk version bumps`() {
        val category = createCategoryUseCase.execute(CreateCategoryCommand(name = "Audio", displayOrder = 1))
        val created =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = category.id,
                    name = "Studio Monitor",
                    sku = "AUD-01",
                    price = BigDecimal("199.00"),
                    stockQuantity = 10,
                ),
            )
        assertEquals(0L, created.version)

        val updated =
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = created.id,
                    categoryId = category.id,
                    name = "Studio Monitor MK2",
                    sku = "AUD-01",
                    price = BigDecimal("219.00"),
                    stockQuantity = 8,
                    expectedVersion = 0L,
                ),
            )
        assertEquals(1L, updated.version)

        // Stale form with version 0 must fail and not overwrite version 1 data
        assertThrows(ProductConcurrencyConflictException::class.java) {
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = created.id,
                    categoryId = category.id,
                    name = "Stale Overwrite",
                    sku = "AUD-01",
                    price = BigDecimal("99.00"),
                    stockQuantity = 1,
                    expectedVersion = 0L,
                ),
            )
        }
        val afterStaleUpdate = getProductDetailUseCase.execute(GetProductDetailQuery(created.id))
        assertEquals("Studio Monitor MK2", afterStaleUpdate.name)
        assertEquals(BigDecimal("219.00"), afterStaleUpdate.price)
        assertEquals(1L, afterStaleUpdate.version)

        // Stale archive with version 0 must fail
        assertThrows(ProductConcurrencyConflictException::class.java) {
            deleteProductUseCase.execute(DeleteProductCommand(created.id, expectedVersion = 0L))
        }
        assertEquals(ProductStatus.ACTIVE.name, getProductDetailUseCase.execute(GetProductDetailQuery(created.id)).status)

        // Bulk image URL update increments product version and invalidates stale version 1 snapshot
        val bulkCount =
            productRepository.updateImageUrlByCategoryId(
                CategoryId.fromString(category.id),
                "https://cdn.example/audio-banner.png",
            )
        assertEquals(1, bulkCount)
        assertEquals(2L, getProductDetailUseCase.execute(GetProductDetailQuery(created.id)).version)

        assertThrows(ProductConcurrencyConflictException::class.java) {
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = created.id,
                    categoryId = category.id,
                    name = "Stale After Bulk",
                    sku = "AUD-01",
                    price = BigDecimal("210.00"),
                    stockQuantity = 8,
                    expectedVersion = 1L,
                ),
            )
        }

        // Archive at latest version 2 succeeds -> version 3; subsequent update with version 2 fails and preserves ARCHIVED
        deleteProductUseCase.execute(DeleteProductCommand(created.id, expectedVersion = 2L))
        val archived = getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(created.id))
        assertEquals(ProductStatus.ARCHIVED.name, archived.status)
        assertEquals(3L, archived.version)

        assertThrows(ProductConcurrencyConflictException::class.java) {
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = created.id,
                    categoryId = category.id,
                    name = "Unarchive Attempt",
                    sku = "AUD-01",
                    price = BigDecimal("219.00"),
                    stockQuantity = 8,
                    status = "ACTIVE",
                    expectedVersion = 2L,
                ),
            )
        }
        assertEquals(ProductStatus.ARCHIVED.name, getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(created.id)).status)
    }

    @Test
    fun `storefront queries expose only active and out of stock products in active categories while admin queries expose all`() {
        val activeCat =
            createCategoryUseCase.execute(
                CreateCategoryCommand(name = "Active Electronics", displayOrder = 1, isActive = true),
            )
        val inactiveCat =
            createCategoryUseCase.execute(
                CreateCategoryCommand(name = "Hidden Seasonal", displayOrder = 2, isActive = false),
            )

        val activeProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = activeCat.id,
                    name = "Active Speaker",
                    sku = "VIS-ACT-1",
                    price = BigDecimal("50.00"),
                    stockQuantity = 10,
                    status = "ACTIVE",
                ),
            )
        val outOfStockProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = activeCat.id,
                    name = "Sold Out Speaker",
                    sku = "VIS-OOS-1",
                    price = BigDecimal("60.00"),
                    stockQuantity = 0,
                    status = "OUT_OF_STOCK",
                ),
            )
        val draftProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = activeCat.id,
                    name = "Draft Speaker",
                    sku = "VIS-DRF-1",
                    price = BigDecimal("70.00"),
                    stockQuantity = 5,
                    status = "DRAFT",
                ),
            )
        val archivedProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = activeCat.id,
                    name = "Archived Speaker",
                    sku = "VIS-ARC-1",
                    price = BigDecimal("80.00"),
                    stockQuantity = 0,
                    status = "ARCHIVED",
                ),
            )
        val hiddenCatActiveProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = inactiveCat.id,
                    name = "Inactive Category Active Item",
                    sku = "HID-ACT-1",
                    price = BigDecimal("40.00"),
                    stockQuantity = 8,
                    status = "ACTIVE",
                ),
            )
        val hiddenCatOosProd =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = inactiveCat.id,
                    name = "Inactive Category OOS Item",
                    sku = "HID-OOS-1",
                    price = BigDecimal("45.00"),
                    stockQuantity = 0,
                    status = "OUT_OF_STOCK",
                ),
            )

        // Public categories: only active
        val publicCategories = listCategoriesUseCase.execute(ListCategoriesQuery())
        assertEquals(listOf(activeCat.id), publicCategories.items.map { it.id })

        // Public products list: only ACTIVE + OUT_OF_STOCK inside active category
        val defaultPublicPage = listProductsUseCase.execute(ListProductsQuery())
        assertEquals(setOf(activeProd.id, outOfStockProd.id), defaultPublicPage.items.mapTo(hashSetOf()) { it.id })

        assertEquals(listOf(activeProd.id), listProductsUseCase.execute(ListProductsQuery(status = "ACTIVE")).items.map { it.id })
        assertEquals(
            listOf(outOfStockProd.id),
            listProductsUseCase.execute(ListProductsQuery(status = "OUT_OF_STOCK")).items.map { it.id },
        )
        for (forbiddenStatus in listOf("ALL", "DRAFT", "ARCHIVED", "draft")) {
            assertThrows(DomainValidationException::class.java) {
                listProductsUseCase.execute(ListProductsQuery(status = forbiddenStatus))
            }
        }

        // Public category-products filter by id & slug
        assertEquals(
            setOf(activeProd.id, outOfStockProd.id),
            listProductsUseCase.execute(ListProductsQuery(categoryId = activeCat.id)).items.mapTo(hashSetOf()) { it.id },
        )
        assertEquals(
            setOf(activeProd.id, outOfStockProd.id),
            listProductsUseCase.execute(ListProductsQuery(categorySlug = activeCat.slug)).items.mapTo(hashSetOf()) { it.id },
        )
        assertEquals(0L, listProductsUseCase.execute(ListProductsQuery(categoryId = inactiveCat.id)).totalElements)
        assertEquals(0L, listProductsUseCase.execute(ListProductsQuery(categorySlug = inactiveCat.slug)).totalElements)

        // Public product detail by id & slug
        assertEquals(activeProd.id, getProductDetailUseCase.execute(GetProductDetailQuery(activeProd.id)).id)
        assertEquals(activeProd.id, getProductDetailUseCase.execute(GetProductDetailQuery(activeProd.slug)).id)
        assertEquals(outOfStockProd.id, getProductDetailUseCase.execute(GetProductDetailQuery(outOfStockProd.id)).id)
        assertEquals(outOfStockProd.id, getProductDetailUseCase.execute(GetProductDetailQuery(outOfStockProd.slug)).id)
        for (hiddenKey in listOf(
            draftProd.id,
            draftProd.slug,
            archivedProd.id,
            archivedProd.slug,
            hiddenCatActiveProd.id,
            hiddenCatActiveProd.slug,
            hiddenCatOosProd.id,
            hiddenCatOosProd.slug,
        )) {
            assertThrows(ProductNotFoundException::class.java) {
                getProductDetailUseCase.execute(GetProductDetailQuery(hiddenKey))
            }
        }

        // Admin queries: full visibility across statuses and inactive categories
        assertEquals(2L, listAdminCategoriesUseCase.execute(ListAdminCategoriesQuery()).totalElements)
        assertEquals(
            listOf(inactiveCat.id),
            listAdminCategoriesUseCase.execute(ListAdminCategoriesQuery(isActive = false)).items.map { it.id },
        )
        assertEquals(6L, listAdminProductsUseCase.execute(ListAdminProductsQuery()).totalElements)
        assertEquals(6L, listAdminProductsUseCase.execute(ListAdminProductsQuery(status = "ALL")).totalElements)
        assertEquals(listOf(draftProd.id), listAdminProductsUseCase.execute(ListAdminProductsQuery(status = "DRAFT")).items.map { it.id })
        assertEquals(
            listOf(archivedProd.id),
            listAdminProductsUseCase.execute(ListAdminProductsQuery(status = "ARCHIVED")).items.map { it.id },
        )
        assertEquals(
            setOf(hiddenCatActiveProd.id, hiddenCatOosProd.id),
            listAdminProductsUseCase.execute(ListAdminProductsQuery(categorySlug = inactiveCat.slug)).items.mapTo(hashSetOf()) { it.id },
        )
        assertEquals(draftProd.id, getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(draftProd.slug)).id)
        assertEquals(archivedProd.id, getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(archivedProd.id)).id)
        assertEquals(
            hiddenCatActiveProd.id,
            getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(hiddenCatActiveProd.slug)).id,
        )
    }

    @Test
    fun `validates price ranges, category existence, and rating bounds`() {
        assertThrows(CategoryNotFoundException::class.java) {
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = CategoryId.generate().value.toString(),
                    name = "Orphan Product",
                    sku = "ORP-01",
                    price = BigDecimal("10.00"),
                ),
            )
        }
        assertThrows(DomainValidationException::class.java) {
            listProductsUseCase.execute(
                ListProductsQuery(minPrice = BigDecimal("100.00"), maxPrice = BigDecimal("50.00")),
            )
        }
        assertThrows(DomainValidationException::class.java) {
            listAdminProductsUseCase.execute(
                ListAdminProductsQuery(minPrice = BigDecimal("100.00"), maxPrice = BigDecimal("50.00")),
            )
        }
        assertThrows(DomainValidationException::class.java) {
            RatingAverage.of(BigDecimal("5.50"))
        }
    }

    @Test
    fun `enforces price precision and bounds, minRating range, UUID filters, and category filter mutual exclusion`() {
        val cat = createCategoryUseCase.execute(CreateCategoryCommand(name = "Validation Cat", displayOrder = 1))

        val zeroPriced =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = cat.id,
                    name = "Free Gift",
                    sku = "VAL-ZERO-01",
                    price = BigDecimal("0"),
                    stockQuantity = 5,
                ),
            )
        assertEquals(BigDecimal("0.00"), zeroPriced.price)

        val maxPriced =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = cat.id,
                    name = "Luxury Vault",
                    sku = "VAL-MAX-01",
                    price = BigDecimal("9999999999999.9900"),
                    stockQuantity = 1,
                ),
            )
        assertEquals(BigDecimal("9999999999999.99"), maxPriced.price)

        val trailingUpdated =
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = zeroPriced.id,
                    categoryId = cat.id,
                    name = "Free Gift Updated",
                    sku = "VAL-ZERO-01",
                    price = BigDecimal("15.500"),
                    stockQuantity = 5,
                    expectedVersion = zeroPriced.version,
                ),
            )
        assertEquals(BigDecimal("15.50"), trailingUpdated.price)

        for (badPrice in listOf(
            BigDecimal("-0.01"),
            BigDecimal("10000000000000.00"),
            BigDecimal("15.555"),
        )) {
            assertThrows(DomainValidationException::class.java) {
                createProductUseCase.execute(
                    CreateProductCommand(
                        categoryId = cat.id,
                        name = "Bad Price Item",
                        sku = "VAL-BAD-SKU",
                        price = badPrice,
                    ),
                )
            }
            assertThrows(DomainValidationException::class.java) {
                updateProductUseCase.execute(
                    UpdateProductCommand(
                        productId = trailingUpdated.id,
                        categoryId = cat.id,
                        name = "Bad Update Price",
                        sku = "VAL-ZERO-01",
                        price = badPrice,
                        stockQuantity = 5,
                        expectedVersion = trailingUpdated.version,
                    ),
                )
            }
        }

        // Non-canonical shorthand UUID must be rejected
        assertThrows(DomainValidationException::class.java) {
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = "1-2-3-4-5",
                    name = "Bad Category UUID",
                    sku = "VAL-CAT-01",
                    price = BigDecimal("10.00"),
                ),
            )
        }

        // Public & admin product list query boundaries
        assertEquals(
            2L,
            listProductsUseCase
                .execute(
                    ListProductsQuery(
                        minPrice = BigDecimal("0.000"),
                        maxPrice = BigDecimal("9999999999999.990"),
                        minRating = BigDecimal("0"),
                    ),
                ).totalElements,
        )
        assertEquals(
            1L,
            listProductsUseCase
                .execute(
                    ListProductsQuery(
                        minPrice = BigDecimal("15.500"),
                        maxPrice = BigDecimal("15.50"),
                    ),
                ).totalElements,
        )
        assertEquals(
            0L,
            listProductsUseCase
                .execute(
                    ListProductsQuery(minRating = BigDecimal("5.00")),
                ).totalElements,
        )

        val invalidQueries =
            listOf(
                ListProductsQuery(minPrice = BigDecimal("-0.01")),
                ListProductsQuery(maxPrice = BigDecimal("-1.00")),
                ListProductsQuery(minPrice = BigDecimal("10000000000000.00")),
                ListProductsQuery(maxPrice = BigDecimal("10000000000000.00")),
                ListProductsQuery(minPrice = BigDecimal("10.125")),
                ListProductsQuery(maxPrice = BigDecimal("10.125")),
                ListProductsQuery(minRating = BigDecimal("-0.01")),
                ListProductsQuery(minRating = BigDecimal("5.01")),
                ListProductsQuery(categoryId = "not-a-uuid"),
                ListProductsQuery(categoryId = "1-2-3-4-5"),
                ListProductsQuery(categoryId = cat.id, categorySlug = cat.slug),
            )
        for (badQuery in invalidQueries) {
            assertThrows(DomainValidationException::class.java) {
                listProductsUseCase.execute(badQuery)
            }
            assertThrows(DomainValidationException::class.java) {
                listAdminProductsUseCase.execute(
                    ListAdminProductsQuery(
                        categoryId = badQuery.categoryId,
                        categorySlug = badQuery.categorySlug,
                        minPrice = badQuery.minPrice,
                        maxPrice = badQuery.maxPrice,
                        minRating = badQuery.minRating,
                    ),
                )
            }
        }

        assertThrows(DomainValidationException::class.java) {
            listCategoriesUseCase.execute(ListCategoriesQuery(parentId = "not-a-uuid"))
        }
        assertThrows(DomainValidationException::class.java) {
            listAdminCategoriesUseCase.execute(ListAdminCategoriesQuery(parentId = "1-2-3-4-5"))
        }
    }

    @Test
    fun `enforces max page size 100 and validates sortBy and sortDir whitelists`() {
        val cat = createCategoryUseCase.execute(CreateCategoryCommand(name = "Electronics", displayOrder = 1))
        createProductUseCase.execute(
            CreateProductCommand(
                categoryId = cat.id,
                name = "Wireless Mouse",
                sku = "WM-01",
                price = BigDecimal("25.00"),
                stockQuantity = 10,
            ),
        )

        val cappedProductPage = listProductsUseCase.execute(ListProductsQuery(page = -3, size = 500, sortBy = "price,asc"))
        assertEquals(0, cappedProductPage.page)
        assertEquals(ListProductsUseCase.MAX_PAGE_SIZE, cappedProductPage.size)

        val namedProductPage = listProductsUseCase.execute(ListProductsQuery(sortBy = "name", sortDir = "asc"))
        assertEquals(1L, namedProductPage.totalElements)

        val cappedCategoryPage =
            listCategoriesUseCase.execute(
                ListCategoriesQuery(page = -1, size = 250, sortBy = "name", sortDir = "desc"),
            )
        assertEquals(0, cappedCategoryPage.page)
        assertEquals(ListCategoriesUseCase.MAX_PAGE_SIZE, cappedCategoryPage.size)

        assertThrows(DomainValidationException::class.java) {
            listProductsUseCase.execute(ListProductsQuery(sortBy = "password", sortDir = "asc"))
        }
        assertThrows(DomainValidationException::class.java) {
            listProductsUseCase.execute(ListProductsQuery(sortBy = "price", sortDir = "random"))
        }
        assertThrows(DomainValidationException::class.java) {
            listCategoriesUseCase.execute(ListCategoriesQuery(sortBy = "password", sortDir = "asc"))
        }
        assertThrows(DomainValidationException::class.java) {
            listCategoriesUseCase.execute(ListCategoriesQuery(sortBy = "name", sortDir = "sideways"))
        }
    }

    @Test
    fun `catalog use cases propagate repository duplicate constraint exceptions after passing pre-check without publishing events`() {
        val racingCategoryRepo =
            object : CategoryRepository by categoryRepository {
                override fun existsBySlug(slug: Slug): Boolean = false

                override fun save(category: Category): Category =
                    throw DuplicateCategorySlugException("Category slug '${category.slug.value}' already exists.")
            }
        val racingCreateCategory = CreateCategoryUseCase(racingCategoryRepo, TimeProvider { clock }, eventPublisher)
        val eventsBeforeCategory = eventPublisher.events.size
        assertThrows(DuplicateCategorySlugException::class.java) {
            racingCreateCategory.execute(CreateCategoryCommand(name = "Racing Cat"))
        }
        assertEquals(eventsBeforeCategory, eventPublisher.events.size)

        val existingCategory = createCategoryUseCase.execute(CreateCategoryCommand(name = "Existing Cat"))
        val existingProduct =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = existingCategory.id,
                    name = "Baseline Product",
                    sku = "BASE-01",
                    price = BigDecimal("19.99"),
                    stockQuantity = 10,
                ),
            )

        val racingProductSkuRepo =
            object : ProductRepository by productRepository {
                override fun existsBySku(sku: Sku): Boolean = false

                override fun existsBySlug(slug: Slug): Boolean = false

                override fun save(product: Product): Product =
                    throw DuplicateProductSkuException("Product SKU '${product.sku.value}' already exists.")

                override fun update(product: Product): Product =
                    throw DuplicateProductSkuException("Product SKU '${product.sku.value}' already exists.")
            }
        val racingCreateProductSku =
            CreateProductUseCase(categoryRepository, racingProductSkuRepo, TimeProvider { clock }, eventPublisher)
        val racingUpdateProductSku =
            UpdateProductUseCase(categoryRepository, racingProductSkuRepo, TimeProvider { clock }, eventPublisher)

        val eventsBeforeSku = eventPublisher.events.size
        assertThrows(DuplicateProductSkuException::class.java) {
            racingCreateProductSku.execute(
                CreateProductCommand(
                    categoryId = existingCategory.id,
                    name = "New Item Sku",
                    sku = "BASE-01",
                    price = BigDecimal("25.00"),
                ),
            )
        }
        assertThrows(DuplicateProductSkuException::class.java) {
            racingUpdateProductSku.execute(
                UpdateProductCommand(
                    productId = existingProduct.id,
                    categoryId = existingCategory.id,
                    name = "Baseline Product",
                    sku = "NEW-SKU",
                    price = BigDecimal("19.99"),
                    stockQuantity = 10,
                    status = "ACTIVE",
                    expectedVersion = 0L,
                ),
            )
        }
        assertEquals(eventsBeforeSku, eventPublisher.events.size)

        val racingProductSlugRepo =
            object : ProductRepository by productRepository {
                override fun existsBySku(sku: Sku): Boolean = false

                override fun existsBySlug(slug: Slug): Boolean = false

                override fun save(product: Product): Product =
                    throw DuplicateProductSlugException("Product slug '${product.slug.value}' already exists.")

                override fun update(product: Product): Product =
                    throw DuplicateProductSlugException("Product slug '${product.slug.value}' already exists.")
            }
        val racingCreateProductSlug =
            CreateProductUseCase(categoryRepository, racingProductSlugRepo, TimeProvider { clock }, eventPublisher)
        val racingUpdateProductSlug =
            UpdateProductUseCase(categoryRepository, racingProductSlugRepo, TimeProvider { clock }, eventPublisher)

        val eventsBeforeSlug = eventPublisher.events.size
        assertThrows(DuplicateProductSlugException::class.java) {
            racingCreateProductSlug.execute(
                CreateProductCommand(
                    categoryId = existingCategory.id,
                    name = "Baseline Product",
                    sku = "UNIQUE-SKU-2",
                    price = BigDecimal("25.00"),
                ),
            )
        }
        assertThrows(DuplicateProductSlugException::class.java) {
            racingUpdateProductSlug.execute(
                UpdateProductCommand(
                    productId = existingProduct.id,
                    categoryId = existingCategory.id,
                    name = "Changed Name",
                    slug = "changed-slug",
                    sku = "BASE-01",
                    price = BigDecimal("19.99"),
                    stockQuantity = 10,
                    status = "ACTIVE",
                    expectedVersion = 0L,
                ),
            )
        }
        assertEquals(eventsBeforeSlug, eventPublisher.events.size)
    }

    @Test
    fun `lifecycle use cases preserve status on update, keep re-archive idempotent, and separate restore from publish`() {
        val cat = createCategoryUseCase.execute(CreateCategoryCommand(name = "Lifecycle Cat", displayOrder = 1))
        val draft =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = cat.id,
                    name = "Draft Widget",
                    sku = "LC-01",
                    price = BigDecimal("20.00"),
                    stockQuantity = 10,
                    status = "DRAFT",
                ),
            )
        assertEquals(ProductStatus.DRAFT.name, draft.status)

        // Updating catalog details with status=null preserves DRAFT instead of auto-activating
        val updatedDraft =
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = draft.id,
                    categoryId = cat.id,
                    name = "Draft Widget Revised",
                    sku = "LC-01",
                    price = BigDecimal("22.00"),
                    stockQuantity = 12,
                    status = null,
                    expectedVersion = draft.version,
                ),
            )
        assertEquals(ProductStatus.DRAFT.name, updatedDraft.status)
        assertEquals(1L, updatedDraft.version)

        // Archiving twice is idempotent and emits ProductArchivedEvent only once without bumping version twice
        deleteProductUseCase.execute(DeleteProductCommand(updatedDraft.id, expectedVersion = 1L))
        assertEquals(2L, getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(updatedDraft.id)).version)
        val eventsAfterArchive = eventPublisher.events.size

        deleteProductUseCase.execute(DeleteProductCommand(updatedDraft.id, expectedVersion = 2L))
        assertEquals(2L, getAdminProductDetailUseCase.execute(GetAdminProductDetailQuery(updatedDraft.id)).version)
        assertEquals(eventsAfterArchive, eventPublisher.events.size)

        // Updating or publishing an archived product is rejected
        assertThrows(DomainValidationException::class.java) {
            updateProductUseCase.execute(
                UpdateProductCommand(
                    productId = updatedDraft.id,
                    categoryId = cat.id,
                    name = "Cannot Reactivate Directly",
                    sku = "LC-01",
                    price = BigDecimal("25.00"),
                    stockQuantity = 5,
                    status = "ACTIVE",
                    expectedVersion = 2L,
                ),
            )
        }
        assertThrows(DomainValidationException::class.java) {
            publishProductUseCase.execute(PublishProductCommand(updatedDraft.id, expectedVersion = 2L))
        }

        // Restoring returns product to DRAFT (still hidden from storefront), then publishing moves it to ACTIVE
        val restored = restoreProductUseCase.execute(RestoreProductCommand(updatedDraft.id, expectedVersion = 2L))
        assertEquals(ProductStatus.DRAFT.name, restored.status)
        assertEquals(3L, restored.version)
        assertEquals(ProductRestoredEvent::class, eventPublisher.events.last()::class)

        val published = publishProductUseCase.execute(PublishProductCommand(updatedDraft.id, expectedVersion = 3L))
        assertEquals(ProductStatus.ACTIVE.name, published.status)
        assertEquals(4L, published.version)
        assertEquals(ProductPublishedEvent::class, eventPublisher.events.last()::class)
    }

    private class RecordingPublisher : DomainEventPublisher {
        val events = mutableListOf<DomainEvent>()

        override fun publish(event: DomainEvent) {
            events += event
        }
    }

    private class InMemoryCategoryRepository :
        CategoryRepository,
        CategoryQueryService {
        val store = linkedMapOf<CategoryId, Category>()

        override fun save(category: Category): Category {
            store[category.id] = category
            return category
        }

        override fun saveAll(categories: Collection<Category>): List<Category> = categories.map(::save)

        override fun findById(id: CategoryId): Category? = store[id]

        override fun findBySlug(slug: Slug): Category? = store.values.firstOrNull { it.slug == slug }

        override fun findAllBySlugs(slugs: Collection<Slug>): Map<Slug, Category> =
            store.values.filter { it.slug in slugs }.associateBy { it.slug }

        override fun existsBySlug(slug: Slug): Boolean = store.values.any { it.slug == slug }

        override fun listPublicCategories(query: ListCategoriesQuery): PageResponseDto<CategoryDto> =
            queryCategories(
                page = query.page,
                size = query.size,
                isActiveFilter = true,
                parentId = query.parentId,
                keyword = query.keyword,
                sortField = query.sortField,
                sortDirection = query.sortDirection,
            )

        override fun listAdminCategories(query: ListAdminCategoriesQuery): PageResponseDto<CategoryDto> =
            queryCategories(
                page = query.page,
                size = query.size,
                isActiveFilter = query.isActive,
                parentId = query.parentId,
                keyword = query.keyword,
                sortField = query.sortField,
                sortDirection = query.sortDirection,
            )

        private fun queryCategories(
            page: Int,
            size: Int,
            isActiveFilter: Boolean?,
            parentId: String?,
            keyword: String?,
            sortField: CategorySortField,
            sortDirection: SortDirection,
        ): PageResponseDto<CategoryDto> {
            val filtered =
                store.values
                    .filter { isActiveFilter == null || it.isActive == isActiveFilter }
                    .filter { parentId == null || it.parentId?.value?.toString() == parentId }
                    .filter {
                        keyword == null ||
                            it.name.contains(keyword, ignoreCase = true) ||
                            it.slug.value.contains(keyword, ignoreCase = true)
                    }.let { list ->
                        val comparator =
                            when (sortField) {
                                CategorySortField.DISPLAY_ORDER -> compareBy<Category> { it.displayOrder }
                                CategorySortField.NAME -> compareBy { it.name }
                                CategorySortField.CREATED_AT -> compareBy { it.createdAt }
                            }.let { base ->
                                if (sortDirection == SortDirection.DESC) base.reversed() else base
                            }.thenBy { it.slug.value }
                        list.sortedWith(comparator)
                    }.map { it.toDto() }
            val totalPages = if (filtered.isEmpty()) 0 else (filtered.size + size - 1) / size
            val fromIndex = (page * size).coerceAtMost(filtered.size)
            val toIndex = ((page + 1) * size).coerceAtMost(filtered.size)
            val slice = filtered.subList(fromIndex, toIndex)
            return PageResponseDto(
                items = slice,
                page = page,
                size = size,
                totalElements = filtered.size.toLong(),
                totalPages = totalPages,
                hasNext = page + 1 < totalPages,
                hasPrevious = page > 0 && totalPages > 0,
            )
        }
    }

    private class InMemoryProductRepository(
        private val categoryRepository: InMemoryCategoryRepository,
    ) : ProductRepository,
        ProductQueryService {
        private val lock = Any()
        private val store = linkedMapOf<ProductId, Product>()

        override fun save(product: Product): Product =
            synchronized(lock) {
                if (product.id in store) {
                    return update(product)
                }
                store[product.id] = product
                product
            }

        override fun update(product: Product): Product =
            synchronized(lock) {
                val existing = store[product.id] ?: throw ProductNotFoundException()
                if (existing.version != product.version) {
                    throw ProductConcurrencyConflictException()
                }
                val updated = rehydrateWith(product, version = existing.version + 1L)
                store[product.id] = updated
                updated
            }

        override fun saveAll(products: Collection<Product>): List<Product> = products.map(::save)

        override fun findById(id: ProductId): Product? = synchronized(lock) { store[id] }

        override fun findBySku(sku: Sku): Product? = store.values.firstOrNull { it.sku == sku }

        override fun existsBySku(sku: Sku): Boolean = store.values.any { it.sku == sku }

        override fun existsBySlug(slug: Slug): Boolean = store.values.any { it.slug == slug }

        override fun findExistingIds(ids: Collection<ProductId>): Set<ProductId> = ids.filterTo(hashSetOf()) { it in store }

        override fun findExistingSkus(skus: Collection<Sku>): Set<Sku> = store.values.map { it.sku }.filterTo(hashSetOf()) { it in skus }

        override fun findExistingSlugs(slugs: Collection<Slug>): Set<Slug> =
            store.values.map { it.slug }.filterTo(hashSetOf()) { it in slugs }

        override fun updateImageUrlByCategoryId(
            categoryId: CategoryId,
            imageUrl: String,
        ): Int =
            synchronized(lock) {
                var count = 0
                store.entries.toList().forEach { (id, product) ->
                    if (product.categoryId == categoryId) {
                        store[id] = rehydrateWith(product, imageUrl = imageUrl, version = product.version + 1L)
                        count++
                    }
                }
                count
            }

        private fun rehydrateWith(
            source: Product,
            imageUrl: String? = source.imageUrl,
            version: Long = source.version,
        ): Product =
            Product.rehydrate(
                id = source.id,
                categoryId = source.categoryId,
                name = source.name,
                slug = source.slug,
                sku = source.sku,
                description = source.description,
                price = source.price,
                stockQuantity = source.stockQuantity,
                averageRating = source.averageRating,
                reviewCount = source.reviewCount,
                imageUrl = imageUrl,
                status = source.status,
                version = version,
                createdAt = source.createdAt,
                updatedAt = source.updatedAt,
            )

        override fun listPublicProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto> {
            val effectiveStatuses = query.statuses.intersect(ProductStatus.PUBLIC_STATUSES)
            return queryProducts(
                page = query.page,
                size = query.size,
                categoryId = query.categoryId,
                categorySlug = query.categorySlug,
                keyword = query.keyword,
                minPrice = query.minPrice,
                maxPrice = query.maxPrice,
                minRating = query.minRating,
                requireActiveCategory = true,
                allowedStatuses = effectiveStatuses,
                sortField = query.sortField,
                sortDirection = query.sortDirection,
            )
        }

        override fun findPublicProductDetail(idOrSlug: String): ProductDetailDto? {
            val product = findByIdOrSlug(idOrSlug) ?: return null
            val category = categoryRepository.findById(product.categoryId) ?: return null
            if (!product.status.isPubliclyVisible || !category.isActive) {
                return null
            }
            return product.toDetailDto(category)
        }

        override fun listAdminProducts(query: ListAdminProductsQuery): PageResponseDto<ProductSummaryDto> =
            queryProducts(
                page = query.page,
                size = query.size,
                categoryId = query.categoryId,
                categorySlug = query.categorySlug,
                keyword = query.keyword,
                minPrice = query.minPrice,
                maxPrice = query.maxPrice,
                minRating = query.minRating,
                requireActiveCategory = false,
                allowedStatuses = query.targetStatus?.let { setOf(it) },
                sortField = query.sortField,
                sortDirection = query.sortDirection,
            )

        override fun findAdminProductDetail(idOrSlug: String): ProductDetailDto? {
            val product = findByIdOrSlug(idOrSlug) ?: return null
            val category = categoryRepository.findById(product.categoryId)!!
            return product.toDetailDto(category)
        }

        private fun findByIdOrSlug(idOrSlug: String): Product? =
            store.values.firstOrNull {
                it.id.value.toString() == idOrSlug || it.slug.value == idOrSlug.lowercase()
            }

        private fun queryProducts(
            page: Int,
            size: Int,
            categoryId: String?,
            categorySlug: String?,
            keyword: String?,
            minPrice: BigDecimal?,
            maxPrice: BigDecimal?,
            minRating: BigDecimal?,
            requireActiveCategory: Boolean,
            allowedStatuses: Set<ProductStatus>?,
            sortField: ProductSortField,
            sortDirection: SortDirection,
        ): PageResponseDto<ProductSummaryDto> {
            val filtered =
                store.values
                    .filter { allowedStatuses == null || it.status in allowedStatuses }
                    .filter { product ->
                        val category = categoryRepository.findById(product.categoryId)
                        !requireActiveCategory || (category != null && category.isActive)
                    }.filter { categoryId == null || it.categoryId.value.toString() == categoryId }
                    .filter { product ->
                        categorySlug == null || categoryRepository.findById(product.categoryId)?.slug?.value == categorySlug
                    }.filter {
                        keyword == null ||
                            it.name.contains(keyword, ignoreCase = true) ||
                            it.sku.value.contains(keyword, ignoreCase = true)
                    }.filter { minPrice == null || it.price.amount >= minPrice }
                    .filter { maxPrice == null || it.price.amount <= maxPrice }
                    .filter { minRating == null || it.averageRating.value >= minRating }
                    .let { list ->
                        val comparator =
                            when (sortField) {
                                ProductSortField.CREATED_AT -> compareBy<Product> { it.createdAt }
                                ProductSortField.PRICE -> compareBy { it.price.amount }
                                ProductSortField.NAME -> compareBy { it.name }
                                ProductSortField.AVERAGE_RATING -> compareBy { it.averageRating.value }
                            }.let { base ->
                                if (sortDirection == SortDirection.DESC) base.reversed() else base
                            }.thenBy { it.slug.value }
                        list.sortedWith(comparator)
                    }.map { product ->
                        ProductSummaryDto(
                            id = product.id.value.toString(),
                            categoryId = product.categoryId.value.toString(),
                            categoryName = categoryRepository.findById(product.categoryId)?.name ?: "General",
                            name = product.name,
                            slug = product.slug.value,
                            sku = product.sku.value,
                            price = product.price.amount,
                            stockQuantity = product.stockQuantity,
                            averageRating = product.averageRating.value,
                            reviewCount = product.reviewCount,
                            imageUrl = product.imageUrl,
                            status = product.status.name,
                            version = product.version,
                        )
                    }
            val totalPages = if (filtered.isEmpty()) 0 else (filtered.size + size - 1) / size
            val fromIndex = (page * size).coerceAtMost(filtered.size)
            val toIndex = ((page + 1) * size).coerceAtMost(filtered.size)
            return PageResponseDto(
                items = filtered.subList(fromIndex, toIndex),
                page = page,
                size = size,
                totalElements = filtered.size.toLong(),
                totalPages = totalPages,
                hasNext = page + 1 < totalPages,
                hasPrevious = page > 0 && totalPages > 0,
            )
        }
    }
}
