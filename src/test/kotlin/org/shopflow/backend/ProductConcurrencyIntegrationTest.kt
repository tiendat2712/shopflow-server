package org.shopflow.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.repository.product.ProductRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.domain.service.dataset.DatasetBatchImportPort
import org.shopflow.backend.domain.service.dataset.ReviewSeedRow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class ProductConcurrencyIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var datasetBatchImportPort: DatasetBatchImportPort

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
                    email = Email.of("concurrency-staff-${userId.value}@shopflow.org"),
                    hashedPassword = HashedPassword("concurrency-unused-password-hash"),
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
        SecurityContextHolder.clearContext()
        staffUserId?.let { userId ->
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
            staffUserId = null
        }
    }

    @Test
    fun `concurrent product updates with same expected version allow one 200 OK and reject the other with 409 conflict`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val categoryId = createCategory("Concurrency Cat $uniqueSuffix").also { createdCategoryIds += it }
            val productId =
                createProduct(
                    categoryId = categoryId,
                    name = "Concurrent Item $uniqueSuffix",
                    sku = "CONC-$uniqueSuffix",
                    price = 80.00,
                    stockQuantity = 10,
                ).also { createdProductIds += it }

            val priceUpdatePayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to categoryId,
                        "name" to "Price Winner $uniqueSuffix",
                        "sku" to "CONC-$uniqueSuffix",
                        "price" to 120.00,
                        "stockQuantity" to 10,
                        "status" to "ACTIVE",
                        "version" to 0,
                    ),
                )
            val stockUpdatePayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to categoryId,
                        "name" to "Stock Winner $uniqueSuffix",
                        "sku" to "CONC-$uniqueSuffix",
                        "price" to 80.00,
                        "stockQuantity" to 45,
                        "status" to "ACTIVE",
                    ),
                )

            val readyLatch = CountDownLatch(2)
            val startGate = CountDownLatch(1)

            val future1 =
                executor.submit(
                    Callable {
                        readyLatch.countDown()
                        assertTrue(startGate.await(5, TimeUnit.SECONDS))
                        performPut(
                            path = "/api/products/$productId",
                            payload = priceUpdatePayload,
                        )
                    },
                )
            val future2 =
                executor.submit(
                    Callable {
                        readyLatch.countDown()
                        assertTrue(startGate.await(5, TimeUnit.SECONDS))
                        performPut(
                            path = "/api/products/$productId",
                            payload = stockUpdatePayload,
                            ifMatch = "\"0\"",
                        )
                    },
                )

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
            startGate.countDown()

            val results = listOf(future1.get(15, TimeUnit.SECONDS), future2.get(15, TimeUnit.SECONDS))
            assertEquals(listOf(200, 409), results.map { it.status }.sorted())

            val winner = results.single { it.status == 200 }
            val loser = results.single { it.status == 409 }

            val winnerJson = objectMapper.readTree(winner.body)["result"]
            assertEquals(1L, winnerJson["version"].asLong())

            val loserJson = objectMapper.readTree(loser.body)
            assertEquals(409, loserJson["status"].asInt())
            assertEquals("PRODUCT_CONCURRENCY_CONFLICT", loserJson["code"].asString())

            val row =
                jdbcTemplate.queryForMap(
                    "SELECT name, price, stock_quantity, version FROM products WHERE id = ?",
                    productId,
                )
            assertEquals(1L, (row["version"] as Number).toLong())
            if (row["name"] == "Price Winner $uniqueSuffix") {
                assertEquals(0, BigDecimal("120.00").compareTo(row["price"] as BigDecimal))
                assertEquals(10, (row["stock_quantity"] as Number).toInt())
            } else {
                assertEquals("Stock Winner $uniqueSuffix", row["name"])
                assertEquals(0, BigDecimal("80.00").compareTo(row["price"] as BigDecimal))
                assertEquals(45, (row["stock_quantity"] as Number).toInt())
            }
        } finally {
            executor.shutdownNow()
            cleanupCatalog(createdProductIds, createdCategoryIds)
        }
    }

    @Test
    fun `concurrent update and archive on same version preserve winner state and reject loser with 409`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()
        val executor = Executors.newFixedThreadPool(2)

        try {
            val categoryId = createCategory("Race Archive Cat $uniqueSuffix").also { createdCategoryIds += it }

            repeat(5) { round ->
                val sku = "RACE-$uniqueSuffix-$round"
                val productId =
                    createProduct(
                        categoryId = categoryId,
                        name = "Race Item $uniqueSuffix $round",
                        sku = sku,
                        price = 90.00,
                        stockQuantity = 15,
                    ).also { createdProductIds += it }

                val updatePayload =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "categoryId" to categoryId,
                            "name" to "Race Item Updated $uniqueSuffix $round",
                            "sku" to sku,
                            "price" to 150.00,
                            "stockQuantity" to 20,
                            "status" to "ACTIVE",
                            "version" to 0,
                        ),
                    )

                val readyLatch = CountDownLatch(2)
                val startGate = CountDownLatch(1)

                val updateFuture =
                    executor.submit(
                        Callable {
                            readyLatch.countDown()
                            assertTrue(startGate.await(5, TimeUnit.SECONDS))
                            performPut("/api/products/$productId", updatePayload)
                        },
                    )
                val archiveFuture =
                    executor.submit(
                        Callable {
                            readyLatch.countDown()
                            assertTrue(startGate.await(5, TimeUnit.SECONDS))
                            performDelete("/api/products/$productId", ifMatch = "\"0\"")
                        },
                    )

                assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
                startGate.countDown()

                val updateResult = updateFuture.get(15, TimeUnit.SECONDS)
                val archiveResult = archiveFuture.get(15, TimeUnit.SECONDS)

                val pair = updateResult.status to archiveResult.status
                assertTrue(
                    pair == (200 to 409) || pair == (409 to 204),
                    "Expected one winner and one 409 conflict, got $pair",
                )

                val row =
                    jdbcTemplate.queryForMap(
                        "SELECT status, price, stock_quantity, version FROM products WHERE id = ?",
                        productId,
                    )
                assertEquals(1L, (row["version"] as Number).toLong())

                if (archiveResult.status == 204) {
                    val loserJson = objectMapper.readTree(updateResult.body)
                    assertEquals("PRODUCT_CONCURRENCY_CONFLICT", loserJson["code"].asString())
                    assertEquals("ARCHIVED", row["status"])
                    assertEquals(0, BigDecimal("90.00").compareTo(row["price"] as BigDecimal))
                    assertEquals(15, (row["stock_quantity"] as Number).toInt())
                } else {
                    val loserJson = objectMapper.readTree(archiveResult.body)
                    assertEquals("PRODUCT_CONCURRENCY_CONFLICT", loserJson["code"].asString())
                    assertEquals("ACTIVE", row["status"])
                    assertEquals(0, BigDecimal("150.00").compareTo(row["price"] as BigDecimal))
                    assertEquals(20, (row["stock_quantity"] as Number).toInt())

                    // Archiving again with latest version 1 succeeds
                    mockMvc
                        .perform(
                            delete("/api/products/$productId")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .param("version", "1"),
                        ).andExpect(status().isNoContent)
                    val archivedRow =
                        jdbcTemplate.queryForMap(
                            "SELECT status, version FROM products WHERE id = ?",
                            productId,
                        )
                    assertEquals("ARCHIVED", archivedRow["status"])
                    assertEquals(2L, (archivedRow["version"] as Number).toLong())
                }
            }
        } finally {
            executor.shutdownNow()
            cleanupCatalog(createdProductIds, createdCategoryIds)
        }
    }

    @Test
    fun `bulk image url update and review count sync increment product version and reject stale PUT`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()
        val createdReviewIds = mutableListOf<String>()

        try {
            val categoryId = createCategory("Bulk Sync Cat $uniqueSuffix").also { createdCategoryIds += it }
            val sku = "BULK-$uniqueSuffix"
            val productId =
                createProduct(
                    categoryId = categoryId,
                    name = "Bulk Sync Item $uniqueSuffix",
                    sku = sku,
                    price = 45.00,
                    stockQuantity = 12,
                ).also { createdProductIds += it }

            val updatedImages =
                productRepository.updateImageUrlByCategoryId(
                    CategoryId.fromString(categoryId),
                    "https://cdn.example/bulk-$uniqueSuffix.png",
                )
            assertEquals(1, updatedImages)

            val afterBulkImage =
                jdbcTemplate.queryForMap(
                    "SELECT image_url, version FROM products WHERE id = ?",
                    productId,
                )
            assertEquals("https://cdn.example/bulk-$uniqueSuffix.png", afterBulkImage["image_url"])
            assertEquals(1L, (afterBulkImage["version"] as Number).toLong())

            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildUpdateBody(categoryId, "Stale V0 $uniqueSuffix", sku, version = 0L)),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("PRODUCT_CONCURRENCY_CONFLICT"))

            val reviewId = UUID.randomUUID().toString().also { createdReviewIds += it }
            val insertedReviews =
                datasetBatchImportPort.insertReviewsIgnore(
                    listOf(
                        ReviewSeedRow(
                            id = reviewId,
                            orderId = UUID.randomUUID().toString(),
                            productId = productId,
                            userId = UUID.randomUUID().toString(),
                            rating = 5,
                            reviewText = "Great product",
                            reviewDate = Instant.parse("2026-11-16T10:00:00Z"),
                        ),
                    ),
                    Instant.parse("2026-11-16T10:00:00Z"),
                )
            assertEquals(1, insertedReviews)

            val syncedProducts = datasetBatchImportPort.syncProductReviewCounts()
            assertTrue(syncedProducts >= 1)

            val afterReviewSync =
                jdbcTemplate.queryForMap(
                    "SELECT review_count, version FROM products WHERE id = ?",
                    productId,
                )
            assertEquals(1, (afterReviewSync["review_count"] as Number).toInt())
            assertEquals(2L, (afterReviewSync["version"] as Number).toLong())

            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildUpdateBody(categoryId, "Stale V1 $uniqueSuffix", sku, version = 1L)),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("PRODUCT_CONCURRENCY_CONFLICT"))

            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildUpdateBody(categoryId, "Fresh V2 $uniqueSuffix", sku, version = 2L)),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.code").value("PRODUCT_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.result.version").value(3))
                .andExpect(jsonPath("$.result.reviewCount").value(1))
        } finally {
            createdReviewIds.forEach { reviewId ->
                jdbcTemplate.update("DELETE FROM reviews WHERE id = ?", reviewId)
            }
            cleanupCatalog(createdProductIds, createdCategoryIds)
        }
    }

    private fun buildUpdateBody(
        categoryId: String,
        name: String,
        sku: String,
        version: Long,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "categoryId" to categoryId,
                "name" to name,
                "sku" to sku,
                "price" to 55.00,
                "stockQuantity" to 12,
                "status" to "ACTIVE",
                "version" to version,
            ),
        )

    private fun createCategory(name: String): String {
        val result =
            mockMvc
                .perform(
                    post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "name" to name,
                                    "displayOrder" to 1,
                                    "isActive" to true,
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["result"]["id"].asString()
    }

    private fun createProduct(
        categoryId: String,
        name: String,
        sku: String,
        price: Double,
        stockQuantity: Int,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "categoryId" to categoryId,
                                    "name" to name,
                                    "sku" to sku,
                                    "price" to price,
                                    "stockQuantity" to stockQuantity,
                                    "status" to "ACTIVE",
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["result"]["id"].asString()
    }

    private fun performPut(
        path: String,
        payload: String,
        ifMatch: String? = null,
    ): HttpCallResult =
        try {
            val result =
                mockMvc
                    .perform(
                        put(path).apply {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            if (ifMatch != null) {
                                header(HttpHeaders.IF_MATCH, ifMatch)
                            }
                            contentType(MediaType.APPLICATION_JSON)
                            content(payload)
                        },
                    ).andReturn()
            HttpCallResult(result.response.status, result.response.contentAsString)
        } finally {
            SecurityContextHolder.clearContext()
        }

    private fun performDelete(
        path: String,
        ifMatch: String? = null,
    ): HttpCallResult =
        try {
            val result =
                mockMvc
                    .perform(
                        delete(path).apply {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            if (ifMatch != null) {
                                header(HttpHeaders.IF_MATCH, ifMatch)
                            }
                        },
                    ).andReturn()
            HttpCallResult(result.response.status, result.response.contentAsString)
        } finally {
            SecurityContextHolder.clearContext()
        }

    private fun cleanupCatalog(
        productIds: List<String>,
        categoryIds: List<String>,
    ) {
        SecurityContextHolder.clearContext()
        productIds.forEach { productId ->
            jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
        }
        categoryIds.forEach { categoryId ->
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
