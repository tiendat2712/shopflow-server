package org.shopflow.backend.application.usecase.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.product.CreateCategoryCommand
import org.shopflow.backend.application.command.product.CreateProductCommand
import org.shopflow.backend.application.command.product.DeleteProductCommand
import org.shopflow.backend.application.command.product.UpdateProductCommand
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.application.dto.product.toDetailDto
import org.shopflow.backend.application.dto.product.toDto
import org.shopflow.backend.application.query.product.CategoryQueryService
import org.shopflow.backend.application.query.product.CategorySortField
import org.shopflow.backend.application.query.product.GetProductDetailQuery
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
import org.shopflow.backend.domain.event.product.ProductUpdatedEvent
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
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
    private lateinit var createProductUseCase: CreateProductUseCase
    private lateinit var updateProductUseCase: UpdateProductUseCase
    private lateinit var deleteProductUseCase: DeleteProductUseCase
    private lateinit var listProductsUseCase: ListProductsUseCase
    private lateinit var getProductDetailUseCase: GetProductDetailUseCase

    @BeforeEach
    fun setUp() {
        categoryRepository = InMemoryCategoryRepository()
        productRepository = InMemoryProductRepository(categoryRepository)
        eventPublisher = RecordingPublisher()
        clock = Instant.parse("2026-11-15T00:00:00Z")
        val timeProvider = TimeProvider { clock }

        createCategoryUseCase = CreateCategoryUseCase(categoryRepository, timeProvider, eventPublisher)
        listCategoriesUseCase = ListCategoriesUseCase(categoryRepository)
        createProductUseCase = CreateProductUseCase(categoryRepository, productRepository, timeProvider, eventPublisher)
        updateProductUseCase = UpdateProductUseCase(categoryRepository, productRepository, timeProvider, eventPublisher)
        deleteProductUseCase = DeleteProductUseCase(productRepository, timeProvider, eventPublisher)
        listProductsUseCase = ListProductsUseCase(productRepository)
        getProductDetailUseCase = GetProductDetailUseCase(productRepository)
    }

    @Test
    fun `category and product lifecycle enforces invariants, publishes events, and supports CQRS queries`() {
        val category = createCategoryUseCase.execute(CreateCategoryCommand(name = "Home & Kitchen", displayOrder = 1))
        assertEquals("home-kitchen", category.slug)
        assertEquals(CategoryCreatedEvent::class, eventPublisher.events.last()::class)
        assertEquals(1L, listCategoriesUseCase.execute(ListCategoriesQuery(onlyActive = true)).totalElements)

        assertThrows(DuplicateCategorySlugException::class.java) {
            createCategoryUseCase.execute(CreateCategoryCommand(name = "Home Kitchen"))
        }

        val created =
            createProductUseCase.execute(
                CreateProductCommand(
                    categoryId = category.id,
                    name = "Ceramic Coffee Dripper",
                    sku = "hk-drip-01",
                    price = BigDecimal("29.995"),
                    stockQuantity = 50,
                ),
            )
        assertEquals("HK-DRIP-01", created.sku)
        assertEquals("ceramic-coffee-dripper", created.slug)
        assertEquals(BigDecimal("30.00"), created.price)
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
                ),
            )
        assertEquals("Ceramic Coffee Dripper V2", updated.name)
        assertEquals(BigDecimal("35.50"), updated.price)
        assertEquals(ProductUpdatedEvent::class, eventPublisher.events.last()::class)

        val detail = getProductDetailUseCase.execute(GetProductDetailQuery(updated.slug))
        assertEquals(updated.id, detail.id)
        assertEquals("Home & Kitchen", detail.categoryName)

        val page = listProductsUseCase.execute(ListProductsQuery(keyword = "dripper", sortBy = "price_asc"))
        assertEquals(1, page.totalElements)

        deleteProductUseCase.execute(DeleteProductCommand(updated.id))
        assertEquals(ProductStatus.ARCHIVED.name, getProductDetailUseCase.execute(GetProductDetailQuery(updated.id)).status)
        assertEquals(ProductArchivedEvent::class, eventPublisher.events.last()::class)
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
            RatingAverage.of(BigDecimal("5.50"))
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

        override fun listCategories(query: ListCategoriesQuery): PageResponseDto<CategoryDto> {
            val filtered =
                store.values
                    .filter { !query.onlyActive || it.isActive }
                    .filter { query.parentId == null || it.parentId?.value?.toString() == query.parentId }
                    .filter {
                        query.keyword == null ||
                            it.name.contains(query.keyword, ignoreCase = true) ||
                            it.slug.value.contains(query.keyword, ignoreCase = true)
                    }.let { list ->
                        val comparator =
                            when (query.sortField) {
                                CategorySortField.DISPLAY_ORDER -> compareBy<Category> { it.displayOrder }
                                CategorySortField.NAME -> compareBy { it.name }
                                CategorySortField.CREATED_AT -> compareBy { it.createdAt }
                            }.let { base ->
                                if (query.sortDirection == SortDirection.DESC) base.reversed() else base
                            }.thenBy { it.slug.value }
                        list.sortedWith(comparator)
                    }.map { it.toDto() }
            val totalPages = if (filtered.isEmpty()) 0 else (filtered.size + query.size - 1) / query.size
            val fromIndex = (query.page * query.size).coerceAtMost(filtered.size)
            val toIndex = ((query.page + 1) * query.size).coerceAtMost(filtered.size)
            val slice = filtered.subList(fromIndex, toIndex)
            return PageResponseDto(
                items = slice,
                page = query.page,
                size = query.size,
                totalElements = filtered.size.toLong(),
                totalPages = totalPages,
                hasNext = query.page + 1 < totalPages,
                hasPrevious = query.page > 0 && totalPages > 0,
            )
        }
    }

    private class InMemoryProductRepository(
        private val categoryRepository: InMemoryCategoryRepository,
    ) : ProductRepository,
        ProductQueryService {
        private val store = linkedMapOf<ProductId, Product>()

        override fun save(product: Product): Product {
            store[product.id] = product
            return product
        }

        override fun saveAll(products: Collection<Product>): List<Product> = products.map(::save)

        override fun findById(id: ProductId): Product? = store[id]

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
        ): Int {
            var count = 0
            store.entries.toList().forEach { (id, product) ->
                if (product.categoryId == categoryId) {
                    store[id] = product.copy(imageUrl = imageUrl)
                    count++
                }
            }
            return count
        }

        override fun listProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto> {
            val filtered =
                store.values
                    .filter { query.status == "ALL" || it.status == ProductStatus.ACTIVE }
                    .filter { query.keyword == null || it.name.contains(query.keyword, ignoreCase = true) }
                    .let { list ->
                        val comparator =
                            when (query.sortField) {
                                ProductSortField.CREATED_AT -> compareBy<Product> { it.createdAt }
                                ProductSortField.PRICE -> compareBy { it.price.amount }
                                ProductSortField.NAME -> compareBy { it.name }
                                ProductSortField.AVERAGE_RATING -> compareBy { it.averageRating.value }
                            }.let { base ->
                                if (query.sortDirection == SortDirection.DESC) base.reversed() else base
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
                        )
                    }
            val totalPages = if (filtered.isEmpty()) 0 else (filtered.size + query.size - 1) / query.size
            val fromIndex = (query.page * query.size).coerceAtMost(filtered.size)
            val toIndex = ((query.page + 1) * query.size).coerceAtMost(filtered.size)
            return PageResponseDto(
                items = filtered.subList(fromIndex, toIndex),
                page = query.page,
                size = query.size,
                totalElements = filtered.size.toLong(),
                totalPages = totalPages,
                hasNext = query.page + 1 < totalPages,
                hasPrevious = query.page > 0 && totalPages > 0,
            )
        }

        override fun findProductDetail(idOrSlug: String): ProductDetailDto? {
            val product =
                store.values.firstOrNull {
                    it.id.value.toString() == idOrSlug || it.slug.value == idOrSlug
                } ?: return null
            val category = categoryRepository.findById(product.categoryId)!!
            return product.toDetailDto(category)
        }
    }
}
