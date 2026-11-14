package org.shopflow.backend.application.usecase.dataset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.shopflow.backend.application.command.dataset.ImportKaggleDatasetCommand
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.RoleId
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.shopflow.backend.domain.service.dataset.DatasetBatchImportPort
import org.shopflow.backend.domain.service.dataset.DatasetSourcePort
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import org.shopflow.backend.domain.service.dataset.OrderItemSeedRow
import org.shopflow.backend.domain.service.dataset.OrderSeedRow
import org.shopflow.backend.domain.service.dataset.PreparedDatasetDirectory
import org.shopflow.backend.domain.service.dataset.ReviewSeedRow
import org.shopflow.backend.domain.service.dataset.UserEventSeedRow
import org.shopflow.backend.domain.service.storage.CategoryBannerGeneratorPort
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.shopflow.backend.domain.service.storage.UploadedImageResult
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class ImportKaggleDatasetUseCaseTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `imports all six dataset CSVs idempotently, converts UUID FKs, and syncs product review counts`() {
        writeSampleDatasetCsvs(tempDir)

        val userRepo = StubUserRepository()
        val roleRepo =
            StubRoleRepository().apply {
                save(Role.create(Role.ROLE_CUSTOMER, "Customer", setOf(Permission.create(Permission.PRODUCT_READ, "Read", "PRODUCT"))))
            }
        val categoryRepo = StubCategoryRepository()
        val productRepo = StubProductRepository()
        val batchImportPort = StubDatasetBatchImportPort(productRepo)
        val passwordHasher = CountingPasswordHasher()
        val liveStorage = RecordingImageStorage(configured = true)
        val bannerGenerator = StubBannerGenerator()

        val useCase =
            ImportKaggleDatasetUseCase(
                datasetSourcePort = LocalDirDatasetSource(tempDir),
                userRepository = userRepo,
                roleRepository = roleRepo,
                categoryRepository = categoryRepo,
                productRepository = productRepo,
                datasetBatchImportPort = batchImportPort,
                passwordHasher = passwordHasher,
                imageStoragePort = liveStorage,
                categoryBannerGeneratorPort = bannerGenerator,
                timeProvider = TimeProvider { Instant.parse("2026-11-15T01:00:00Z") },
            )

        val firstRun = useCase.execute(ImportKaggleDatasetCommand())
        assertTrue(firstRun.cloudinaryLiveUploadUsed)
        assertEquals(2, firstRun.usersInserted)
        assertEquals(1, firstRun.usersSkipped) // duplicate email handling
        assertEquals(2, firstRun.categoriesInserted)
        assertEquals(0, firstRun.categoriesExisting)
        assertEquals(3, firstRun.productsInserted)
        assertEquals(0, firstRun.productsSkipped)
        assertEquals(1, firstRun.ordersInserted)
        assertEquals(0, firstRun.ordersSkipped)
        assertEquals(1, firstRun.orderItemsInserted)
        assertEquals(0, firstRun.orderItemsSkipped)
        assertEquals(1, firstRun.reviewsInserted)
        assertEquals(0, firstRun.reviewsSkipped)
        assertEquals(1, firstRun.productReviewCountsUpdated)
        assertEquals(2, firstRun.userEventsInserted)
        assertEquals(0, firstRun.userEventsSkipped)
        assertTrue(firstRun.stagedCsvFiles.isEmpty())
        assertEquals(1, passwordHasher.encodeCalls)
        assertEquals(2, liveStorage.uploadedFolders.size)

        val importedGadget = productRepo.findById(ProductId.deterministic("P000001"))!!
        assertEquals(BigDecimal("157.89"), importedGadget.price.amount)
        assertEquals("https://cdn.example/categories/electronics.png", importedGadget.imageUrl)
        assertEquals(1, importedGadget.reviewCount)

        val storedOrder = batchImportPort.orders[DatasetUuidConverter.orderId("O1")]!!
        assertEquals(UserId.deterministic("U000001").value.toString(), storedOrder.userId)
        assertEquals(BigDecimal("157.89"), storedOrder.totalAmount)

        val storedOrderItem = batchImportPort.orderItems[DatasetUuidConverter.orderItemId("OI1")]!!
        assertEquals(storedOrder.id, storedOrderItem.orderId)
        assertEquals(importedGadget.id.value.toString(), storedOrderItem.productId)

        val storedReview = batchImportPort.reviews[DatasetUuidConverter.reviewId("R1")]!!
        assertEquals(storedOrder.id, storedReview.orderId)
        assertEquals(importedGadget.id.value.toString(), storedReview.productId)
        assertEquals("Works \"great\", love it!", storedReview.reviewText)

        val homeKitchenCategory = categoryRepo.findBySlug(Slug.of("Home & Kitchen", Slug.CATEGORY_MAX_LENGTH))!!
        assertEquals(DatasetUuidConverter.toCategoryUuid("Home & Kitchen"), homeKitchenCategory.id.value)

        val secondRun = useCase.execute(ImportKaggleDatasetCommand())
        assertEquals(0, secondRun.usersInserted)
        assertEquals(3, secondRun.usersSkipped)
        assertEquals(0, secondRun.categoriesInserted)
        assertEquals(2, secondRun.categoriesExisting)
        assertEquals(0, secondRun.productsInserted)
        assertEquals(3, secondRun.productsSkipped)
        assertEquals(3, secondRun.productsImageUpdated)
        assertEquals(0, secondRun.ordersInserted)
        assertEquals(1, secondRun.ordersSkipped)
        assertEquals(0, secondRun.orderItemsInserted)
        assertEquals(1, secondRun.orderItemsSkipped)
        assertEquals(0, secondRun.reviewsInserted)
        assertEquals(1, secondRun.reviewsSkipped)
        assertEquals(0, secondRun.productReviewCountsUpdated)
        assertEquals(0, secondRun.userEventsInserted)
        assertEquals(2, secondRun.userEventsSkipped)
    }

    @Test
    fun `falls back to deterministic category URLs when Cloudinary is not configured`() {
        writeSampleDatasetCsvs(tempDir)

        val userRepo = StubUserRepository()
        val roleRepo = StubRoleRepository().apply { save(Role.create(Role.ROLE_CUSTOMER, "Customer", emptySet())) }
        val categoryRepo = StubCategoryRepository()
        val productRepo = StubProductRepository()
        val unconfiguredStorage = RecordingImageStorage(configured = false)

        val useCase =
            ImportKaggleDatasetUseCase(
                datasetSourcePort = LocalDirDatasetSource(tempDir),
                userRepository = userRepo,
                roleRepository = roleRepo,
                categoryRepository = categoryRepo,
                productRepository = productRepo,
                datasetBatchImportPort = StubDatasetBatchImportPort(productRepo),
                passwordHasher = CountingPasswordHasher(),
                imageStoragePort = unconfiguredStorage,
                categoryBannerGeneratorPort = StubBannerGenerator(),
                timeProvider = TimeProvider { Instant.parse("2026-11-15T01:00:00Z") },
            )

        val result = useCase.execute(ImportKaggleDatasetCommand())
        assertFalse(result.cloudinaryLiveUploadUsed)
        assertEquals(0, unconfiguredStorage.uploadedFolders.size)
        assertEquals(
            "https://fallback.local/electronics.png",
            result.categoryImageUrls["electronics"],
        )
    }

    @Test
    fun `preserves existing canonical 36-char UUIDs in CSV rows while converting raw identifiers`() {
        val existingUserUuid = "11111111-2222-3333-4444-555555555555"
        val existingProductUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val existingOrderUuid = "bbbbbbbb-2222-3333-4444-555555555555"

        Files.writeString(
            tempDir.resolve("users.csv"),
            """
            user_id,name,email,gender,city,signup_date
            $existingUserUuid,Uuid User,uuid.user@example.net,Female,Hanoi,2025-05-01
            """.trimIndent(),
        )
        Files.writeString(
            tempDir.resolve("products.csv"),
            """
            product_id,product_name,category,brand,price,rating
            $existingProductUuid,UUID Native Item,Home & Kitchen,BrandX,99.00,4.50
            """.trimIndent(),
        )
        Files.writeString(
            tempDir.resolve("orders.csv"),
            """
            order_id,user_id,order_date,order_status,total_amount
            $existingOrderUuid,$existingUserUuid,2025-09-09T14:52:37.292731,completed,99.00
            O00000001,U000001,2025-09-10T10:00:00.000001,processing,15.00
            """.trimIndent(),
        )
        Files.writeString(
            tempDir.resolve("order_items.csv"),
            """
            order_item_id,order_id,product_id,user_id,quantity,item_price,item_total
            I00000001,$existingOrderUuid,$existingProductUuid,$existingUserUuid,1,99.00,99.00
            """.trimIndent(),
        )
        Files.writeString(
            tempDir.resolve("reviews.csv"),
            """
            review_id,order_id,product_id,user_id,rating,review_text,review_date
            R00000001,O00000001,P000001,U000001,4,Solid item,2025-09-11T09:30:00.123456
            """.trimIndent(),
        )
        Files.writeString(
            tempDir.resolve("events.csv"),
            """
            event_id,user_id,product_id,event_type,event_timestamp
            E00000001,$existingUserUuid,$existingProductUuid,view,2025-09-09T14:50:00.000001
            """.trimIndent(),
        )

        val userRepo = StubUserRepository()
        val roleRepo = StubRoleRepository().apply { save(Role.create(Role.ROLE_CUSTOMER, "Customer", emptySet())) }
        val categoryRepo = StubCategoryRepository()
        val productRepo = StubProductRepository()
        val batchImportPort = StubDatasetBatchImportPort(productRepo)

        val useCase =
            ImportKaggleDatasetUseCase(
                datasetSourcePort = LocalDirDatasetSource(tempDir),
                userRepository = userRepo,
                roleRepository = roleRepo,
                categoryRepository = categoryRepo,
                productRepository = productRepo,
                datasetBatchImportPort = batchImportPort,
                passwordHasher = CountingPasswordHasher(),
                imageStoragePort = RecordingImageStorage(configured = false),
                categoryBannerGeneratorPort = StubBannerGenerator(),
                timeProvider = TimeProvider { Instant.parse("2026-11-15T01:00:00Z") },
            )

        val result = useCase.execute(ImportKaggleDatasetCommand())
        assertEquals(1, result.usersInserted)
        assertEquals(1, result.productsInserted)
        assertEquals(2, result.ordersInserted)
        assertEquals(1, result.orderItemsInserted)
        assertEquals(1, result.reviewsInserted)
        assertEquals(1, result.userEventsInserted)

        val savedUser = userRepo.findById(UserId.fromString(existingUserUuid))
        assertEquals(existingUserUuid, savedUser?.id?.value?.toString())
        val savedProduct = productRepo.findById(ProductId.fromString(existingProductUuid))
        assertEquals(existingProductUuid, savedProduct?.id?.value?.toString())
        assertEquals(DatasetUuidConverter.toCategoryUuid("Home & Kitchen"), savedProduct?.categoryId?.value)

        assertTrue(batchImportPort.orders.containsKey(existingOrderUuid))
        assertEquals(existingUserUuid, batchImportPort.orders[existingOrderUuid]?.userId)

        val convertedOrderId = DatasetUuidConverter.orderId("O00000001")
        assertTrue(batchImportPort.orders.containsKey(convertedOrderId))
        assertEquals(DatasetUuidConverter.userId("U000001"), batchImportPort.orders[convertedOrderId]?.userId)

        val convertedItem = batchImportPort.orderItems[DatasetUuidConverter.orderItemId("I00000001")]!!
        assertEquals(existingOrderUuid, convertedItem.orderId)
        assertEquals(existingProductUuid, convertedItem.productId)
        assertEquals(existingUserUuid, convertedItem.userId)

        val convertedReview = batchImportPort.reviews[DatasetUuidConverter.reviewId("R00000001")]!!
        assertEquals(convertedOrderId, convertedReview.orderId)
        assertEquals(DatasetUuidConverter.productId("P000001"), convertedReview.productId)
        assertEquals(DatasetUuidConverter.userId("U000001"), convertedReview.userId)

        val convertedEvent = batchImportPort.userEvents[DatasetUuidConverter.eventId("E00000001")]!!
        assertEquals(existingUserUuid, convertedEvent.userId)
        assertEquals(existingProductUuid, convertedEvent.productId)
    }

    private fun writeSampleDatasetCsvs(dir: Path) {
        Files.writeString(
            dir.resolve("users.csv"),
            """
            user_id,name,email,gender,city,signup_date
            U000001,"Hill, Angel",angel@example.net,Other,New Roberttown,2025-03-13
            U000002,Tran Binh,binh@example.net,Male,Ho Chi Minh,2025-04-01
            U000003,Duplicate Email,angel@example.net,Female,Ha Noi,2025-04-02
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("products.csv"),
            """
            product_id,product_name,category,brand,price,rating
            P000001,"Astra ""Pro"" Headphones",Electronics,Astra,157.89,4.08
            P000002,Smart Kettle,Home & Kitchen,Lumina,49.50,4.75
            P000003,USB-C Hub,Electronics,Astra,24.00,4.20
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("orders.csv"),
            "order_id,user_id,order_date,order_status,total_amount\nO1,U000001,2025-09-09T14:52:37.292731,processing,157.89\n",
        )
        Files.writeString(
            dir.resolve("order_items.csv"),
            "order_item_id,order_id,product_id,user_id,quantity,item_price,item_total\nOI1,O1,P000001,U000001,1,157.89,157.89\n",
        )
        Files.writeString(
            dir.resolve("reviews.csv"),
            "review_id,order_id,product_id,user_id,rating,review_text,review_date\nR1,O1,P000001,U000001,5,\"Works \"\"great\"\", love it!\",2025-09-10T08:15:00.123456\n",
        )
        Files.writeString(
            dir.resolve("events.csv"),
            "event_id,user_id,product_id,event_type,event_timestamp\nE1,U000001,P000001,view,2025-09-09\nE2,U000002,P000002,cart,2025-09-10T10:11:12.000001\n",
        )
    }

    private class LocalDirDatasetSource(
        private val dir: Path,
    ) : DatasetSourcePort {
        override fun prepareDatasetDirectory(forceDownload: Boolean): PreparedDatasetDirectory =
            PreparedDatasetDirectory(dir, downloadedFromRemote = false)

        override fun defaultUserPassword(): String = "ShopFlow@123!"
    }

    private class StubDatasetBatchImportPort(
        private val productRepository: StubProductRepository,
    ) : DatasetBatchImportPort {
        val orders = linkedMapOf<String, OrderSeedRow>()
        val orderItems = linkedMapOf<String, OrderItemSeedRow>()
        val reviews = linkedMapOf<String, ReviewSeedRow>()
        val userEvents = linkedMapOf<String, UserEventSeedRow>()

        override fun insertOrdersIgnore(
            rows: List<OrderSeedRow>,
            auditedAt: Instant,
        ): Int = rows.count { orders.putIfAbsent(it.id, it) == null }

        override fun insertOrderItemsIgnore(
            rows: List<OrderItemSeedRow>,
            auditedAt: Instant,
        ): Int = rows.count { orderItems.putIfAbsent(it.id, it) == null }

        override fun insertReviewsIgnore(
            rows: List<ReviewSeedRow>,
            auditedAt: Instant,
        ): Int = rows.count { reviews.putIfAbsent(it.id, it) == null }

        override fun syncProductReviewCounts(): Int {
            val countsByProductId = reviews.values.groupingBy { it.productId }.eachCount()
            return productRepository.syncReviewCounts(countsByProductId)
        }

        override fun insertUserEventsIgnore(
            rows: List<UserEventSeedRow>,
            auditedAt: Instant,
        ): Int = rows.count { userEvents.putIfAbsent(it.id, it) == null }
    }

    private class CountingPasswordHasher : PasswordHasher {
        var encodeCalls = 0

        override fun encode(rawPassword: String): HashedPassword {
            encodeCalls++
            return HashedPassword("hashed:$rawPassword")
        }

        override fun matches(
            rawPassword: String,
            hashedPassword: HashedPassword,
        ): Boolean = hashedPassword.value == "hashed:$rawPassword"
    }

    private class RecordingImageStorage(
        private val configured: Boolean,
    ) : ImageStoragePort {
        val uploadedFolders = mutableListOf<String>()

        override fun isConfigured(): Boolean = configured

        override fun uploadImage(
            fileBytes: ByteArray,
            fileName: String,
            folder: String,
            publicIdHint: String?,
        ): UploadedImageResult {
            uploadedFolders += folder
            return UploadedImageResult(
                url = "https://cdn.example/$folder/$fileName",
                publicId = "shopflow/$folder/$fileName",
                format = "png",
                bytes = fileBytes.size.toLong(),
            )
        }

        override fun deleteImage(publicId: String) = Unit
    }

    private class StubBannerGenerator : CategoryBannerGeneratorPort {
        override fun generateBannerPng(
            categoryName: String,
            categorySlug: String,
        ): ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        override fun defaultFallbackUrl(categorySlug: String): String = "https://fallback.local/$categorySlug.png"
    }

    private class StubUserRepository : UserRepository {
        private val store = linkedMapOf<UserId, User>()

        override fun findByEmail(email: Email): User? = store.values.firstOrNull { it.email == email }

        override fun findById(id: UserId): User? = store[id]

        override fun existsByEmail(email: Email): Boolean = store.values.any { it.email == email }

        override fun findExistingEmails(emails: Collection<String>): Set<String> =
            store.values.map { it.email.value.lowercase() }.filterTo(hashSetOf()) { it in emails }

        override fun findExistingIds(ids: Collection<UserId>): Set<UserId> = ids.filterTo(hashSetOf()) { it in store }

        override fun save(user: User): User {
            store[user.id] = user
            return user
        }

        override fun saveAll(users: Collection<User>): List<User> = users.map(::save)
    }

    private class StubRoleRepository : RoleRepository {
        private val store = linkedMapOf<RoleId, Role>()

        override fun findByName(name: String): Role? = store.values.firstOrNull { it.name == name }

        override fun findById(id: RoleId): Role? = store[id]

        override fun findAll(): List<Role> = store.values.toList()

        override fun save(role: Role): Role {
            store[role.id] = role
            return role
        }
    }

    private class StubCategoryRepository : CategoryRepository {
        private val store = linkedMapOf<CategoryId, Category>()

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
    }

    private class StubProductRepository : ProductRepository {
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
            store.entries.toList().forEach { (id, p) ->
                if (p.categoryId == categoryId) {
                    store[id] = p.copy(imageUrl = imageUrl)
                    count++
                }
            }
            return count
        }

        fun syncReviewCounts(countsByProductId: Map<String, Int>): Int {
            var updated = 0
            store.entries.toList().forEach { (id, product) ->
                val expected = countsByProductId[id.value.toString()] ?: 0
                if (product.reviewCount != expected) {
                    store[id] = product.copy(reviewCount = expected)
                    updated++
                }
            }
            return updated
        }
    }
}
