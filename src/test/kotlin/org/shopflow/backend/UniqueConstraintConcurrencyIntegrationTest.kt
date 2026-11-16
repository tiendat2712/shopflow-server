package org.shopflow.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.model.product.Category
import org.shopflow.backend.domain.model.product.Money
import org.shopflow.backend.domain.model.product.Product
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.model.product.Sku
import org.shopflow.backend.domain.model.product.Slug
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.repository.product.CategoryRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.presentation.exceptionhandler.GlobalExceptionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class UniqueConstraintConcurrencyIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private lateinit var mockMvc: MockMvc
    private lateinit var staffToken: String
    private var staffUserId: String? = null

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        assertEquals("shopflow_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val userId = UserId.generate()
        val now = Instant.now()
        val staffUser =
            userRepository.save(
                User(
                    id = userId,
                    email = Email.of("unique-staff-${userId.value}@shopflow.org"),
                    hashedPassword = HashedPassword("unique-unused-password-hash"),
                    roles = setOf(staffRole),
                    status = UserStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        staffUserId = staffUser.id.value.toString()
        staffToken =
            tokenProvider.generateAccessToken(
                userId = staffUser.id,
                roles = setOf(staffRole.name),
                permissions = staffRole.getPermissionCodes(),
            )
    }

    @AfterEach
    fun tearDown() {
        staffUserId?.let { userId ->
            cleanupUsers(listOf(userId))
            staffUserId = null
        }
    }

    @Test
    fun `repository save and update translate real MySQL unique constraint violations bypassing pre-checks`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()
        val now = Instant.parse("2026-11-16T12:00:00Z")

        try {
            val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
            val duplicateEmail = "DirectRace-$suffix@shopflow.org"
            val (firstUser, _) =
                User.register(
                    email = Email.of(duplicateEmail),
                    hashedPassword = HashedPassword("hashed-pass"),
                    defaultRole = customerRole,
                    now = now,
                )
            userRepository.save(firstUser)
            createdUserIds += firstUser.id.value.toString()

            val (secondUser, _) =
                User.register(
                    email = Email.of(duplicateEmail.lowercase()),
                    hashedPassword = HashedPassword("hashed-pass-2"),
                    defaultRole = customerRole,
                    now = now,
                )
            assertThrows(UserAlreadyExistsException::class.java) {
                userRepository.save(secondUser)
            }
            createdUserIds += secondUser.id.value.toString()

            val (firstCat, _) =
                Category.create(
                    name = "Direct Cat $suffix",
                    slug = Slug.of("direct-cat-$suffix", Slug.CATEGORY_MAX_LENGTH),
                    now = now,
                )
            categoryRepository.save(firstCat)
            createdCategoryIds += firstCat.id.value.toString()

            val (secondCat, _) =
                Category.create(
                    name = "Second Direct Cat $suffix",
                    slug = firstCat.slug,
                    now = now,
                )
            assertThrows(DuplicateCategorySlugException::class.java) {
                categoryRepository.save(secondCat)
            }
            createdCategoryIds += secondCat.id.value.toString()

            val sharedTextKey = "samekey-$suffix"
            val (firstProd, _) =
                Product.create(
                    categoryId = firstCat.id,
                    name = "First Direct Item $suffix",
                    slug = Slug.of(sharedTextKey),
                    sku = Sku.of(sharedTextKey),
                    price = Money.of(BigDecimal("49.00")),
                    now = now,
                )
            productRepository.save(firstProd)
            createdProductIds += firstProd.id.value.toString()

            val (dupSkuProd, _) =
                Product.create(
                    categoryId = firstCat.id,
                    name = "Dup Sku Item $suffix",
                    slug = Slug.of("distinct-slug-sku-$suffix"),
                    sku = firstProd.sku,
                    price = Money.of(BigDecimal("59.00")),
                    now = now,
                )
            assertThrows(DuplicateProductSkuException::class.java) {
                productRepository.save(dupSkuProd)
            }
            createdProductIds += dupSkuProd.id.value.toString()

            val (dupSlugProd, _) =
                Product.create(
                    categoryId = firstCat.id,
                    name = "Dup Slug Item $suffix",
                    slug = firstProd.slug,
                    sku = Sku.of("DISTINCT-SKU-$suffix"),
                    price = Money.of(BigDecimal("69.00")),
                    now = now,
                )
            assertThrows(DuplicateProductSlugException::class.java) {
                productRepository.save(dupSlugProd)
            }
            createdProductIds += dupSlugProd.id.value.toString()

            val (secondProd, _) =
                Product.create(
                    categoryId = firstCat.id,
                    name = "Update Target Item $suffix",
                    slug = Slug.of("update-target-$suffix"),
                    sku = Sku.of("UPD-TARGET-$suffix"),
                    price = Money.of(BigDecimal("79.00")),
                    now = now,
                )
            val savedSecond = productRepository.save(secondProd)
            createdProductIds += savedSecond.id.value.toString()

            val (skuCollisionUpdate, _) =
                savedSecond.updateCatalogDetails(
                    categoryId = firstCat.id,
                    name = savedSecond.name,
                    slug = savedSecond.slug,
                    sku = firstProd.sku,
                    description = null,
                    price = Money.of(BigDecimal("89.00")),
                    stockQuantity = 10,
                    imageUrl = null,
                    expectedVersion = 0L,
                    now = now.plusSeconds(10),
                )
            assertThrows(DuplicateProductSkuException::class.java) {
                productRepository.update(skuCollisionUpdate)
            }

            val (slugCollisionUpdate, _) =
                savedSecond.updateCatalogDetails(
                    categoryId = firstCat.id,
                    name = savedSecond.name,
                    slug = firstProd.slug,
                    sku = savedSecond.sku,
                    description = null,
                    price = Money.of(BigDecimal("99.00")),
                    stockQuantity = 10,
                    imageUrl = null,
                    expectedVersion = 0L,
                    now = now.plusSeconds(10),
                )
            assertThrows(DuplicateProductSlugException::class.java) {
                productRepository.update(slugCollisionUpdate)
            }

            val reloadedSecond = productRepository.findById(savedSecond.id)!!
            assertEquals(0L, reloadedSecond.version)
            assertEquals(savedSecond.sku, reloadedSecond.sku)
            assertEquals(savedSecond.slug, reloadedSecond.slug)
        } finally {
            cleanupCatalog(createdProductIds, createdCategoryIds)
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `non-duplicate DataIntegrityViolationExceptions are not converted into duplicate errors and do not leak SQL`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()
        val now = Instant.parse("2026-11-16T12:00:00Z")

        try {
            val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
            val (overlongPasswordUser, _) =
                User.register(
                    email = Email.of("trunc-$suffix@shopflow.org"),
                    hashedPassword = HashedPassword("x".repeat(300)),
                    defaultRole = customerRole,
                    now = now,
                )
            createdUserIds += overlongPasswordUser.id.value.toString()

            assertThrows(DataIntegrityViolationException::class.java) {
                userRepository.save(overlongPasswordUser)
            }

            val (category, _) =
                Category.create(
                    name = "Neg Cat $suffix",
                    slug = Slug.of("neg-cat-$suffix", Slug.CATEGORY_MAX_LENGTH),
                    now = now,
                )
            categoryRepository.save(category)
            createdCategoryIds += category.id.value.toString()

            val sharedId = ProductId.generate()
            val (firstProd, _) =
                Product.create(
                    id = sharedId,
                    categoryId = category.id,
                    name = "Neg Item $suffix",
                    slug = Slug.of("neg-item-$suffix"),
                    sku = Sku.of("NEG-ITEM-$suffix"),
                    price = Money.of(BigDecimal("15.00")),
                    now = now,
                )
            productRepository.save(firstProd)
            createdProductIds += sharedId.value.toString()

            // Duplicate PRIMARY key with distinct SKU and slug must NOT map to DuplicateProductSku/SlugException.
            val (primaryKeyCollision, _) =
                Product.create(
                    id = sharedId,
                    categoryId = category.id,
                    name = "Neg Item PK $suffix",
                    slug = Slug.of("neg-item-pk-$suffix"),
                    sku = Sku.of("NEG-PK-$suffix"),
                    price = Money.of(BigDecimal("25.00")),
                    now = now,
                )
            assertThrows(DataIntegrityViolationException::class.java) {
                productRepository.saveAll(listOf(primaryKeyCollision))
            }

            // Oversized price is rejected early at HTTP boundary with 400 Bad Request, while raw DB integrity errors return 500 safely.
            val overflowPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to category.id.value.toString(),
                        "name" to "Overflow Item $suffix",
                        "sku" to "OVF-$suffix",
                        "price" to BigDecimal("100000000000000.00"),
                        "stockQuantity" to 5,
                        "status" to "ACTIVE",
                    ),
                )
            val overflowResult = performPost("/api/products", overflowPayload, bearerToken = staffToken)
            assertEquals(400, overflowResult.status)

            val rawIntegrityResponse =
                GlobalExceptionHandler().handleDataIntegrityViolation(
                    DataIntegrityViolationException(
                        "could not execute statement [insert into products ...]",
                        SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452),
                    ),
                )
            assertEquals(500, rawIntegrityResponse.statusCode.value())
            val sanitizedJson = objectMapper.writeValueAsString(rawIntegrityResponse.body)
            assertNoInternalDetailsLeaked(sanitizedJson)
            val errorJson = objectMapper.readTree(sanitizedJson)
            assertEquals(500, errorJson["status"].asInt())
            assertEquals("INTERNAL_SERVER_ERROR", errorJson["code"].asString())
            assertTrue(errorJson["result"].isNull)
        } finally {
            cleanupCatalog(createdProductIds, createdCategoryIds)
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `concurrent register requests with same email allow one 201 and reject loser with 409 USER_ALREADY_EXISTS`() {
        val trackedEmails = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8)}-$round"
                val canonicalEmail = "raceuser-$suffix@shopflow.org"
                val mixedCaseEmail = "RaceUser-$suffix@ShopFlow.org"
                trackedEmails += canonicalEmail

                val payloadA =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "email" to canonicalEmail,
                            "password" to "Password123!",
                            "fullName" to "Winner One",
                        ),
                    )
                val payloadB =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "email" to mixedCaseEmail,
                            "password" to "Password123!",
                            "fullName" to "Contender Two",
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPost("/api/auth/register", payloadA) },
                        second = { performPost("/api/auth/register", payloadB) },
                    )
                assertEquals(listOf(201, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("USER_ALREADY_EXISTS", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val persistedCount =
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = ?",
                        Int::class.java,
                        canonicalEmail,
                    )
                assertEquals(1, persistedCount)
            }
        } finally {
            executor.shutdownNow()
            cleanupUsersByEmails(trackedEmails)
        }
    }

    @Test
    fun `concurrent category creations with same slug allow one 201 and reject loser with 409 DUPLICATE_CATEGORY_SLUG`() {
        val trackedSlugs = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8)}-$round"
                val slug = "race-cat-$suffix"
                trackedSlugs += slug

                val payloadA =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "Race Cat A $suffix",
                            "slug" to slug,
                            "displayOrder" to 1,
                            "isActive" to true,
                        ),
                    )
                val payloadB =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "Race Cat B $suffix",
                            "slug" to slug,
                            "displayOrder" to 2,
                            "isActive" to true,
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPost("/api/categories", payloadA, bearerToken = staffToken) },
                        second = { performPost("/api/categories", payloadB, bearerToken = staffToken) },
                    )
                assertEquals(listOf(201, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("DUPLICATE_CATEGORY_SLUG", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val persistedCount =
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM categories WHERE slug = ?",
                        Int::class.java,
                        slug,
                    )
                assertEquals(1, persistedCount)
            }
        } finally {
            executor.shutdownNow()
            trackedSlugs.forEach { slug ->
                jdbcTemplate.update("DELETE FROM categories WHERE slug = ?", slug)
            }
        }
    }

    @Test
    fun `concurrent product creations with same SKU or same slug allow one 201 and reject loser with 409`() {
        val trackedCategoryIds = mutableListOf<String>()
        val trackedSkus = mutableListOf<String>()
        val trackedSlugs = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val categoryId = createCategory("Race Catalog ${UUID.randomUUID().toString().take(8)}").also { trackedCategoryIds += it }

            // 1. Concurrent SKU collision with distinct slugs
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8)}-$round"
                val sharedSku = "RACE-SKU-$suffix"
                trackedSkus += sharedSku

                val payloadA =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Race Item Alpha $suffix",
                            "slug" to "race-sku-alpha-$suffix",
                            "sku" to sharedSku,
                            "price" to 35.00,
                            "stockQuantity" to 5,
                            "status" to "ACTIVE",
                        ),
                    )
                val payloadB =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Race Item Beta $suffix",
                            "slug" to "race-sku-beta-$suffix",
                            "sku" to sharedSku,
                            "price" to 45.00,
                            "stockQuantity" to 7,
                            "status" to "ACTIVE",
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPost("/api/products", payloadA, bearerToken = staffToken) },
                        second = { performPost("/api/products", payloadB, bearerToken = staffToken) },
                    )
                assertEquals(listOf(201, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("DUPLICATE_PRODUCT_SKU", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val count =
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM products WHERE sku = ?",
                        Int::class.java,
                        sharedSku,
                    )
                assertEquals(1, count)
            }

            // 2. Concurrent slug collision with distinct SKUs
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8)}-$round"
                val sharedSlug = "race-item-slug-$suffix"
                trackedSlugs += sharedSlug

                val payloadA =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Slug Race Alpha $suffix",
                            "slug" to sharedSlug,
                            "sku" to "RACE-SLUG-A-$suffix",
                            "price" to 55.00,
                            "stockQuantity" to 8,
                            "status" to "ACTIVE",
                        ),
                    )
                val payloadB =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Slug Race Beta $suffix",
                            "slug" to sharedSlug,
                            "sku" to "RACE-SLUG-B-$suffix",
                            "price" to 65.00,
                            "stockQuantity" to 9,
                            "status" to "ACTIVE",
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPost("/api/products", payloadA, bearerToken = staffToken) },
                        second = { performPost("/api/products", payloadB, bearerToken = staffToken) },
                    )
                assertEquals(listOf(201, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("DUPLICATE_PRODUCT_SLUG", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val count =
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM products WHERE slug = ?",
                        Int::class.java,
                        sharedSlug,
                    )
                assertEquals(1, count)
            }
        } finally {
            executor.shutdownNow()
            trackedSkus.forEach { sku -> jdbcTemplate.update("DELETE FROM products WHERE sku = ?", sku) }
            trackedSlugs.forEach { slug -> jdbcTemplate.update("DELETE FROM products WHERE slug = ?", slug) }
            trackedCategoryIds.forEach { catId -> jdbcTemplate.update("DELETE FROM categories WHERE id = ?", catId) }
        }
    }

    @Test
    fun `concurrent product updates claiming same target SKU or target slug allow one 200 and roll back loser with 409`() {
        val trackedCategoryIds = mutableListOf<String>()
        val trackedProductIds = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val categoryId = createCategory("Race Upd Cat ${UUID.randomUUID().toString().take(8)}").also { trackedCategoryIds += it }

            // 1. Two distinct products concurrently updated to claim the same target SKU
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8).uppercase()}-$round"
                val id1 =
                    createProduct(
                        categoryId = categoryId,
                        name = "Upd Sku One $suffix",
                        slug = "upd-sku-one-${suffix.lowercase()}",
                        sku = "ORIG-SKU-1-$suffix",
                        price = 10.00,
                    ).also { trackedProductIds += it }
                val id2 =
                    createProduct(
                        categoryId = categoryId,
                        name = "Upd Sku Two $suffix",
                        slug = "upd-sku-two-${suffix.lowercase()}",
                        sku = "ORIG-SKU-2-$suffix",
                        price = 20.00,
                    ).also { trackedProductIds += it }

                val targetSku = "CLAIMED-SKU-$suffix"
                val putOne =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Upd Sku One New $suffix",
                            "slug" to "upd-sku-one-${suffix.lowercase()}",
                            "sku" to targetSku,
                            "price" to 110.00,
                            "stockQuantity" to 3,
                            "status" to "ACTIVE",
                            "version" to 0,
                        ),
                    )
                val putTwo =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Upd Sku Two New $suffix",
                            "slug" to "upd-sku-two-${suffix.lowercase()}",
                            "sku" to targetSku,
                            "price" to 120.00,
                            "stockQuantity" to 4,
                            "status" to "ACTIVE",
                            "version" to 0,
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPut("/api/products/$id1", putOne) },
                        second = { performPut("/api/products/$id2", putTwo) },
                    )
                assertEquals(listOf(200, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("DUPLICATE_PRODUCT_SKU", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val row1 = jdbcTemplate.queryForMap("SELECT sku, price, version FROM products WHERE id = ?", id1)
                val row2 = jdbcTemplate.queryForMap("SELECT sku, price, version FROM products WHERE id = ?", id2)
                if (results[0].status == 200) {
                    assertEquals(targetSku, row1["sku"])
                    assertEquals(1L, (row1["version"] as Number).toLong())
                    assertEquals("ORIG-SKU-2-$suffix", row2["sku"])
                    assertEquals(0L, (row2["version"] as Number).toLong())
                    assertEquals(0, BigDecimal("20.00").compareTo(row2["price"] as BigDecimal))
                } else {
                    assertEquals(targetSku, row2["sku"])
                    assertEquals(1L, (row2["version"] as Number).toLong())
                    assertEquals("ORIG-SKU-1-$suffix", row1["sku"])
                    assertEquals(0L, (row1["version"] as Number).toLong())
                    assertEquals(0, BigDecimal("10.00").compareTo(row1["price"] as BigDecimal))
                }
            }

            // 2. Two distinct products concurrently updated to claim the same target slug
            repeat(5) { round ->
                val suffix = "${UUID.randomUUID().toString().take(8)}-$round"
                val id1 =
                    createProduct(
                        categoryId = categoryId,
                        name = "Upd Slug One $suffix",
                        slug = "orig-slug-1-$suffix",
                        sku = "UPD-SLG-1-$suffix",
                        price = 30.00,
                    ).also { trackedProductIds += it }
                val id2 =
                    createProduct(
                        categoryId = categoryId,
                        name = "Upd Slug Two $suffix",
                        slug = "orig-slug-2-$suffix",
                        sku = "UPD-SLG-2-$suffix",
                        price = 40.00,
                    ).also { trackedProductIds += it }

                val targetSlug = "claimed-slug-$suffix"
                val putOne =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Upd Slug One New $suffix",
                            "slug" to targetSlug,
                            "sku" to "UPD-SLG-1-$suffix",
                            "price" to 130.00,
                            "stockQuantity" to 5,
                            "status" to "ACTIVE",
                            "version" to 0,
                        ),
                    )
                val putTwo =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Upd Slug Two New $suffix",
                            "slug" to targetSlug,
                            "sku" to "UPD-SLG-2-$suffix",
                            "price" to 140.00,
                            "stockQuantity" to 6,
                            "status" to "ACTIVE",
                            "version" to 0,
                        ),
                    )

                val results =
                    runConcurrently(
                        executor,
                        first = { performPut("/api/products/$id1", putOne) },
                        second = { performPut("/api/products/$id2", putTwo) },
                    )
                assertEquals(listOf(200, 409), results.map { it.status }.sorted())

                val loser = results.single { it.status == 409 }
                val loserJson = objectMapper.readTree(loser.body)
                assertEquals(409, loserJson["status"].asInt())
                assertEquals("DUPLICATE_PRODUCT_SLUG", loserJson["code"].asString())
                assertNoInternalDetailsLeaked(loser.body)

                val row1 = jdbcTemplate.queryForMap("SELECT slug, price, version FROM products WHERE id = ?", id1)
                val row2 = jdbcTemplate.queryForMap("SELECT slug, price, version FROM products WHERE id = ?", id2)
                if (results[0].status == 200) {
                    assertEquals(targetSlug, row1["slug"])
                    assertEquals(1L, (row1["version"] as Number).toLong())
                    assertEquals("orig-slug-2-$suffix", row2["slug"])
                    assertEquals(0L, (row2["version"] as Number).toLong())
                    assertEquals(0, BigDecimal("40.00").compareTo(row2["price"] as BigDecimal))
                } else {
                    assertEquals(targetSlug, row2["slug"])
                    assertEquals(1L, (row2["version"] as Number).toLong())
                    assertEquals("orig-slug-1-$suffix", row1["slug"])
                    assertEquals(0L, (row1["version"] as Number).toLong())
                    assertEquals(0, BigDecimal("30.00").compareTo(row1["price"] as BigDecimal))
                }
            }
        } finally {
            executor.shutdownNow()
            cleanupCatalog(trackedProductIds, trackedCategoryIds)
        }
    }

    private fun runConcurrently(
        executor: java.util.concurrent.ExecutorService,
        first: () -> HttpCallResult,
        second: () -> HttpCallResult,
    ): List<HttpCallResult> {
        val readyLatch = CountDownLatch(2)
        val startGate = CountDownLatch(1)

        val future1 =
            executor.submit(
                Callable {
                    readyLatch.countDown()
                    assertTrue(startGate.await(5, TimeUnit.SECONDS))
                    first()
                },
            )
        val future2 =
            executor.submit(
                Callable {
                    readyLatch.countDown()
                    assertTrue(startGate.await(5, TimeUnit.SECONDS))
                    second()
                },
            )

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
        startGate.countDown()
        return listOf(future1.get(15, TimeUnit.SECONDS), future2.get(15, TimeUnit.SECONDS))
    }

    private fun performPost(
        path: String,
        payload: String,
        bearerToken: String? = null,
    ): HttpCallResult =
        try {
            val result =
                mockMvc
                    .perform(
                        post(path).apply {
                            if (bearerToken != null) {
                                header(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")
                            }
                            contentType(MediaType.APPLICATION_JSON)
                            content(payload)
                        },
                    ).andReturn()
            HttpCallResult(result.response.status, result.response.contentAsString)
        } finally {
            SecurityContextHolder.clearContext()
        }

    private fun performPut(
        path: String,
        payload: String,
    ): HttpCallResult =
        try {
            val result =
                mockMvc
                    .perform(
                        put(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload),
                    ).andReturn()
            HttpCallResult(result.response.status, result.response.contentAsString)
        } finally {
            SecurityContextHolder.clearContext()
        }

    private fun createCategory(name: String): String {
        val result =
            performPost(
                "/api/categories",
                objectMapper.writeValueAsString(
                    mapOf(
                        "name" to name,
                        "displayOrder" to 1,
                        "isActive" to true,
                    ),
                ),
                bearerToken = staffToken,
            )
        assertEquals(201, result.status)
        return objectMapper.readTree(result.body)["result"]["id"].asString()
    }

    private fun createProduct(
        categoryId: String,
        name: String,
        slug: String,
        sku: String,
        price: Double,
    ): String {
        val result =
            performPost(
                "/api/products",
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to categoryId,
                        "name" to name,
                        "slug" to slug,
                        "sku" to sku,
                        "price" to price,
                        "stockQuantity" to 10,
                        "status" to "ACTIVE",
                    ),
                ),
                bearerToken = staffToken,
            )
        assertEquals(201, result.status)
        return objectMapper.readTree(result.body)["result"]["id"].asString()
    }

    private fun assertNoInternalDetailsLeaked(body: String) {
        val lower = body.lowercase()
        val forbiddenTokens =
            listOf(
                "users",
                "categories",
                "products",
                "idx_users_email",
                "uk_categories_slug",
                "uk_products_sku",
                "uk_products_slug",
                "uk_",
                "duplicate entry",
                "for key",
                "insert into",
                "update ",
                "sql",
                "hibernate",
                "constraint",
            )
        for (token in forbiddenTokens) {
            assertFalse(
                lower.contains(token),
                "Response leaked internal detail '$token': $body",
            )
        }
    }

    private fun cleanupUsersByEmails(emails: List<String>) {
        SecurityContextHolder.clearContext()
        emails.distinct().forEach { email ->
            val ids =
                jdbcTemplate.query(
                    "SELECT id FROM users WHERE email = ?",
                    { rs, _ -> rs.getString("id") },
                    email,
                )
            cleanupUsers(ids)
        }
    }

    private fun cleanupUsers(userIds: List<String>) {
        SecurityContextHolder.clearContext()
        userIds.distinct().forEach { userId ->
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
        }
    }

    private fun cleanupCatalog(
        productIds: List<String>,
        categoryIds: List<String>,
    ) {
        SecurityContextHolder.clearContext()
        productIds.distinct().forEach { productId ->
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
        }
        categoryIds.distinct().forEach { categoryId ->
            jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId)
        }
    }

    private data class HttpCallResult(
        val status: Int,
        val body: String,
    )

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun overrideTestDatabase(registry: DynamicPropertyRegistry) {
            val host = System.getenv("DB_HOST")?.takeIf { it.isNotBlank() } ?: "127.0.0.2"
            val port = System.getenv("DB_PORT")?.takeIf { it.isNotBlank() } ?: "3307"
            registry.add("spring.datasource.url") {
                "jdbc:mysql://$host:$port/shopflow_test?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
            }
        }
    }
}
