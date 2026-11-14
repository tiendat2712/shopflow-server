package org.shopflow.backend

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.product.CategoryId
import org.shopflow.backend.domain.model.product.ProductId
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.domain.service.dataset.DatasetUuidConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
class MediaAndDatasetImportE2ETest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        SecurityContextHolder.clearContext()
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
    }

    @Test
    fun `media upload enforces RBAC and file validation while Kaggle admin import runs idempotently`() {
        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val adminRole = roleRepository.findByName(Role.ROLE_ADMIN)!!

        val customerToken =
            tokenProvider.generateAccessToken(
                userId = UserId.generate(),
                roles = setOf(customerRole.name),
                permissions = customerRole.getPermissionCodes(),
            )
        val staffToken =
            tokenProvider.generateAccessToken(
                userId = UserId.generate(),
                roles = setOf(staffRole.name),
                permissions = staffRole.getPermissionCodes(),
            )
        val adminToken =
            tokenProvider.generateAccessToken(
                userId = UserId.generate(),
                roles = setOf(adminRole.name),
                permissions = adminRole.getPermissionCodes(),
            )

        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02)
        val validPng = MockMultipartFile("file", "sample.png", "image/png", pngHeader)
        val fakeText = MockMultipartFile("file", "fake.png", "image/png", "not-png".toByteArray())

        mockMvc
            .perform(multipart("/api/media/upload").file(validPng).param("folder", "products"))
            .andExpect(status().isUnauthorized)

        mockMvc
            .perform(
                multipart("/api/media/upload")
                    .file(validPng)
                    .param("folder", "products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
            ).andExpect(status().isForbidden)

        mockMvc
            .perform(
                multipart("/api/media/upload")
                    .file(fakeText)
                    .param("folder", "products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
            ).andExpect(status().isBadRequest)

        mockMvc
            .perform(
                multipart("/api/media/upload")
                    .file(validPng)
                    .param("folder", "products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
            ).andExpect(status().isBadGateway)

        writeSampleDatasetTo(tempDatasetDir)

        val importedUser1Id = UserId.deterministic("E2E_U001").value.toString()
        val importedUser2Id = UserId.deterministic("E2E_U002").value.toString()
        val importedProduct1Id = ProductId.deterministic("E2E_P001").value.toString()
        val importedProduct2Id = ProductId.deterministic("E2E_P002").value.toString()
        val importedOrder1Id = DatasetUuidConverter.orderId("O_E2E_1")
        val importedOrderItem1Id = DatasetUuidConverter.orderItemId("OI_E2E_1")
        val importedReview1Id = DatasetUuidConverter.reviewId("R_E2E_1")
        val importedEvent1Id = DatasetUuidConverter.eventId("EV_E2E_1")
        val catElecId = CategoryId.deterministic("E2E Electronics").value.toString()
        val catBooksId = CategoryId.deterministic("E2E Books").value.toString()

        try {
            mockMvc
                .perform(
                    post("/api/admin/datasets/import-kaggle")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
                ).andExpect(status().isForbidden)

            mockMvc
                .perform(
                    post("/api/admin/datasets/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
                ).andExpect(status().isForbidden)

            val importPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "forceDownload" to false,
                        "uploadCategoryPlaceholders" to true,
                    ),
                )

            val firstResult =
                mockMvc
                    .perform(
                        post("/api/admin/datasets/import-kaggle")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(importPayload),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.downloadedFromRemote").value(false))
                    .andExpect(jsonPath("$.cloudinaryLiveUploadUsed").value(false))
                    .andExpect(jsonPath("$.usersInserted").value(2))
                    .andExpect(jsonPath("$.categoriesInserted").value(2))
                    .andExpect(jsonPath("$.productsInserted").value(2))
                    .andExpect(jsonPath("$.ordersInserted").value(1))
                    .andExpect(jsonPath("$.orderItemsInserted").value(1))
                    .andExpect(jsonPath("$.reviewsInserted").value(1))
                    .andExpect(jsonPath("$.productReviewCountsUpdated").value(1))
                    .andExpect(jsonPath("$.userEventsInserted").value(1))
                    .andReturn()

            val json = objectMapper.readTree(firstResult.response.contentAsString)
            assertTrue(json["categoryImageUrls"].has("e2e-electronics"))
            assertEquals(0, json["stagedCsvFiles"].size())

            val persistedJoinRows =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM users u
                    JOIN orders o ON o.user_id = u.id
                    JOIN order_items oi ON oi.order_id = o.id
                    JOIN products p ON p.id = oi.product_id
                    WHERE o.id = ?
                    """.trimIndent(),
                    Int::class.java,
                    importedOrder1Id,
                )
            assertEquals(1, persistedJoinRows)

            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "email" to "e2e.u001@example.net",
                                    "password" to "ShopFlow@123!",
                                ),
                            ),
                        ),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").isNotEmpty)

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categorySlug", "e2e-electronics"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].reviewCount").value(1))

            mockMvc
                .perform(
                    post("/api/admin/datasets/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.usersInserted").value(0))
                .andExpect(jsonPath("$.usersSkipped").value(2))
                .andExpect(jsonPath("$.productsInserted").value(0))
                .andExpect(jsonPath("$.productsSkipped").value(2))
                .andExpect(jsonPath("$.ordersInserted").value(0))
                .andExpect(jsonPath("$.ordersSkipped").value(1))
                .andExpect(jsonPath("$.orderItemsInserted").value(0))
                .andExpect(jsonPath("$.orderItemsSkipped").value(1))
                .andExpect(jsonPath("$.reviewsInserted").value(0))
                .andExpect(jsonPath("$.reviewsSkipped").value(1))
                .andExpect(jsonPath("$.productReviewCountsUpdated").value(0))
                .andExpect(jsonPath("$.userEventsInserted").value(0))
                .andExpect(jsonPath("$.userEventsSkipped").value(1))
        } finally {
            SecurityContextHolder.clearContext()
            jdbcTemplate.update("DELETE FROM user_events WHERE id = ?", importedEvent1Id)
            jdbcTemplate.update("DELETE FROM reviews WHERE id = ?", importedReview1Id)
            jdbcTemplate.update("DELETE FROM order_items WHERE id = ?", importedOrderItem1Id)
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", importedOrder1Id)
            listOf(importedProduct1Id, importedProduct2Id).forEach {
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", it)
            }
            listOf(catElecId, catBooksId).forEach {
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", it)
            }
            listOf(importedUser1Id, importedUser2Id).forEach {
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", it)
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", it)
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", it)
            }
        }
    }

    private fun writeSampleDatasetTo(dir: Path) {
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("users.csv"),
            """
            user_id,name,email,gender,city,signup_date
            E2E_U001,Le Van An,e2e.u001@example.net,Male,Ho Chi Minh,2025-03-13
            E2E_U002,Pham Thi Binh,e2e.u002@example.net,Female,Da Nang,2025-04-10
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("products.csv"),
            """
            product_id,product_name,category,brand,price,rating
            E2E_P001,E2E Mechanical Switch,E2E Electronics,ShopBrand,49.99,4.50
            E2E_P002,E2E Clean Architecture Guide,E2E Books,TechPress,32.00,4.85
            """.trimIndent(),
        )
        Files.writeString(
            dir.resolve("orders.csv"),
            "order_id,user_id,order_date,order_status,total_amount\nO_E2E_1,E2E_U001,2025-09-09T14:52:37.292731,processing,49.99\n",
        )
        Files.writeString(
            dir.resolve("order_items.csv"),
            "order_item_id,order_id,product_id,user_id,quantity,item_price,item_total\nOI_E2E_1,O_E2E_1,E2E_P001,E2E_U001,1,49.99,49.99\n",
        )
        Files.writeString(
            dir.resolve("reviews.csv"),
            "review_id,order_id,product_id,user_id,rating,review_text,review_date\nR_E2E_1,O_E2E_1,E2E_P001,E2E_U001,5,Great tactile feel,2025-09-10T10:00:00.123456\n",
        )
        Files.writeString(
            dir.resolve("events.csv"),
            "event_id,user_id,product_id,event_type,event_timestamp\nEV_E2E_1,E2E_U001,E2E_P001,purchase,2025-09-09T14:53:00.000001\n",
        )
    }

    companion object {
        private val tempDatasetDir: Path = Files.createTempDirectory("shopflow-e2e-dataset-")

        @JvmStatic
        @DynamicPropertySource
        fun registerDatasetDirectory(registry: DynamicPropertyRegistry) {
            registry.add("app.dataset.local-dir") { tempDatasetDir.toAbsolutePath().toString() }
        }

        @JvmStatic
        @AfterAll
        fun cleanTempDirectory() {
            tempDatasetDir.toFile().deleteRecursively()
        }
    }
}
