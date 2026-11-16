package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

@SpringBootTest
class ProductCatalogEndToEndTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

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
    fun `public read and staff write RBAC lifecycle works with MySQL auditing and soft delete`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!

        val createdUserIds = mutableListOf<String>()
        var createdCategoryId: String? = null
        var createdProductId: String? = null

        try {
            val customerToken = issuePersistedUserToken(customerRole, createdUserIds)
            val staffActorId = UserId.generate()
            val staffToken = issuePersistedUserToken(staffRole, createdUserIds, staffActorId)
            mockMvc.perform(get("/api/categories")).andExpect(status().isOk)
            mockMvc.perform(get("/api/products")).andExpect(status().isOk)

            val categoryPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "name" to "E2E Gadgets $uniqueSuffix",
                        "description" to "Test category",
                        "displayOrder" to 1,
                    ),
                )

            mockMvc
                .perform(
                    post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload),
                ).andExpect(status().isForbidden)

            val catResult =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(categoryPayload),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.code").value("CATEGORY_CREATE_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.result.name").value("E2E Gadgets $uniqueSuffix"))
                    .andReturn()

            createdCategoryId = objectMapper.readTree(catResult.response.contentAsString)["result"]["id"].asString()

            val createProductPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to createdCategoryId,
                        "name" to "Wireless Keyboard $uniqueSuffix",
                        "sku" to "E2E-KB-$uniqueSuffix",
                        "description" to "Mechanical keyboard",
                        "price" to 89.99,
                        "stockQuantity" to 40,
                        "imageUrl" to "https://res.cloudinary.com/demo/image/upload/keyboard.png",
                    ),
                )

            mockMvc
                .perform(
                    post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductPayload),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            val prodResult =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createProductPayload),
                    ).andExpect(status().isCreated)
                    .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                    .andExpect(jsonPath("$.code").value("PRODUCT_CREATE_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.result.price").value(89.99))
                    .andExpect(jsonPath("$.result.stockQuantity").value(40))
                    .andExpect(jsonPath("$.result.version").value(0))
                    .andReturn()

            val prodJson = objectMapper.readTree(prodResult.response.contentAsString)["result"]
            createdProductId = prodJson["id"].asString()
            val productSlug = prodJson["slug"].asString()

            mockMvc
                .perform(get("/api/products/$productSlug"))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.code").value("PRODUCT_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.result.id").value(createdProductId))
                .andExpect(jsonPath("$.result.categoryName").value("E2E Gadgets $uniqueSuffix"))
                .andExpect(jsonPath("$.result.version").value(0))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("keyword", uniqueSuffix)
                        .param("sortBy", "price_asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("PRODUCT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.result.items[0].id").value(createdProductId))
                .andExpect(jsonPath("$.result.items[0].version").value(0))

            val updatePayloadWithoutVersion =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to createdCategoryId,
                        "name" to "Wireless Keyboard Pro $uniqueSuffix",
                        "sku" to "E2E-KB-$uniqueSuffix",
                        "price" to 99.50,
                        "stockQuantity" to 35,
                        "status" to "ACTIVE",
                    ),
                )

            // Missing version / If-Match must be rejected with 400 Bad Request
            mockMvc
                .perform(
                    put("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayloadWithoutVersion),
                ).andExpect(status().isBadRequest)

            mockMvc
                .perform(
                    delete("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                ).andExpect(status().isBadRequest)

            val updatePayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to createdCategoryId,
                        "name" to "Wireless Keyboard Pro $uniqueSuffix",
                        "sku" to "E2E-KB-$uniqueSuffix",
                        "price" to 99.50,
                        "stockQuantity" to 35,
                        "status" to "ACTIVE",
                        "version" to 0,
                    ),
                )

            mockMvc
                .perform(
                    put("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.code").value("PRODUCT_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.result.name").value("Wireless Keyboard Pro $uniqueSuffix"))
                .andExpect(jsonPath("$.result.price").value(99.5))
                .andExpect(jsonPath("$.result.version").value(1))

            // Re-using stale version 0 must fail with 409 Conflict and stable code
            mockMvc
                .perform(
                    put("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PRODUCT_CONCURRENCY_CONFLICT"))

            val auditRow =
                jdbcTemplate.queryForMap(
                    "SELECT created_by, updated_by, price, version FROM products WHERE id = ?",
                    createdProductId,
                )
            assertEquals(staffActorId.value.toString(), auditRow["created_by"])
            assertEquals(staffActorId.value.toString(), auditRow["updated_by"])
            assertNotNull(auditRow["price"])
            assertEquals(1L, (auditRow["version"] as Number).toLong())

            mockMvc
                .perform(
                    delete("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .header(HttpHeaders.IF_MATCH, "\"1\""),
                ).andExpect(status().isNoContent)

            mockMvc
                .perform(get("/api/products/$createdProductId"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))

            mockMvc
                .perform(get("/api/products/$productSlug"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))

            mockMvc
                .perform(
                    get("/api/admin/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.code").value("ADMIN_PRODUCT_DETAIL_SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.result.version").value(2))
        } finally {
            SecurityContextHolder.clearContext()
            if (createdProductId != null) {
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", createdProductId)
            }
            if (createdCategoryId != null) {
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", createdCategoryId)
            }
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `pagination sorting and whitelist validation work across products and categories GET endpoints`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            val staffToken = issuePersistedUserToken(staffRole, createdUserIds)
            val alphaCatRes =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "name" to "Alpha Cat $uniqueSuffix",
                                        "displayOrder" to 1,
                                    ),
                                ),
                            ),
                    ).andExpect(status().isCreated)
                    .andReturn()
            val alphaCatJson = objectMapper.readTree(alphaCatRes.response.contentAsString)["result"]
            val alphaCatId = alphaCatJson["id"].asString()
            val alphaCatSlug = alphaCatJson["slug"].asString()
            createdCategoryIds += alphaCatId

            val betaCatRes =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "name" to "Beta Cat $uniqueSuffix",
                                        "displayOrder" to 2,
                                    ),
                                ),
                            ),
                    ).andExpect(status().isCreated)
                    .andReturn()
            createdCategoryIds += objectMapper.readTree(betaCatRes.response.contentAsString)["result"]["id"].asString()

            val productSpecs =
                listOf(
                    Triple("Bravo Item $uniqueSuffix", "E2E-P2-$uniqueSuffix", 59.99),
                    Triple("Alpha Item $uniqueSuffix", "E2E-P1-$uniqueSuffix", 99.99),
                    Triple("Charlie Item $uniqueSuffix", "E2E-P3-$uniqueSuffix", 19.99),
                )
            for ((name, sku, price) in productSpecs) {
                val res =
                    mockMvc
                        .perform(
                            post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    objectMapper.writeValueAsString(
                                        mapOf(
                                            "categoryId" to alphaCatId,
                                            "name" to name,
                                            "sku" to sku,
                                            "price" to price,
                                            "stockQuantity" to 10,
                                        ),
                                    ),
                                ),
                        ).andExpect(status().isCreated)
                        .andReturn()
                createdProductIds += objectMapper.readTree(res.response.contentAsString)["result"]["id"].asString()
                Thread.sleep(25)
            }
            val bravoId = createdProductIds[0]
            val alphaId = createdProductIds[1]
            val charlieId = createdProductIds[2]

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("sortDir", "asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("PRODUCT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].price").value(19.99))
                .andExpect(jsonPath("$.result.items[1].price").value(59.99))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(2))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.hasPrevious").value(false))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("sortDir", "asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].price").value(99.99))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.hasPrevious").value(true))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "price")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.items[0].price").value(99.99))
                .andExpect(jsonPath("$.result.items[1].price").value(59.99))
                .andExpect(jsonPath("$.result.items[2].price").value(19.99))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "name")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.items[0].name").value("Charlie Item $uniqueSuffix"))
                .andExpect(jsonPath("$.result.items[1].name").value("Bravo Item $uniqueSuffix"))
                .andExpect(jsonPath("$.result.items[2].name").value("Alpha Item $uniqueSuffix"))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.items[0].id").value(charlieId))
                .andExpect(jsonPath("$.result.items[1].id").value(alphaId))
                .andExpect(jsonPath("$.result.items[2].id").value(bravoId))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("size", "500"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.size").value(100))

            mockMvc
                .perform(get("/api/products").param("sortBy", "nonExistentField"))
                .andExpect(status().isBadRequest)

            mockMvc
                .perform(get("/api/products").param("sortBy", "price").param("sortDir", "sideways"))
                .andExpect(status().isBadRequest)

            mockMvc
                .perform(
                    get("/api/categories/$alphaCatSlug/products")
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("sortDir", "asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("CATEGORY_PRODUCT_LIST_SUCCESS"))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.items[0].price").value(19.99))

            mockMvc
                .perform(
                    get("/api/categories")
                        .param("keyword", uniqueSuffix)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "name")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("CATEGORY_LIST_SUCCESS"))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.hasPrevious").value(false))
                .andExpect(jsonPath("$.result.items[0].name").value("Beta Cat $uniqueSuffix"))

            mockMvc
                .perform(get("/api/categories").param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest)
        } finally {
            SecurityContextHolder.clearContext()
            createdProductIds.forEach { productId ->
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
            }
            createdCategoryIds.forEach { categoryId ->
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId)
            }
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `storefront hides draft archived and inactive category products while admin endpoints enforce PRODUCT_WRITE`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!

        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            val customerToken = issuePersistedUserToken(customerRole, createdUserIds)
            val staffToken = issuePersistedUserToken(staffRole, createdUserIds)
            val activeCat =
                createCategoryViaApi(
                    staffToken = staffToken,
                    name = "Active Store Cat $uniqueSuffix",
                    displayOrder = 1,
                    isActive = true,
                ).also { createdCategoryIds += it.first }
            val inactiveCat =
                createCategoryViaApi(
                    staffToken = staffToken,
                    name = "Inactive Hidden Cat $uniqueSuffix",
                    displayOrder = 2,
                    isActive = false,
                ).also { createdCategoryIds += it.first }

            val activeProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = activeCat.first,
                    name = "Storefront Active $uniqueSuffix",
                    sku = "SF-ACT-$uniqueSuffix",
                    status = "ACTIVE",
                ).also { createdProductIds += it.first }
            val oosProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = activeCat.first,
                    name = "Storefront OOS $uniqueSuffix",
                    sku = "SF-OOS-$uniqueSuffix",
                    status = "OUT_OF_STOCK",
                ).also { createdProductIds += it.first }
            val draftProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = activeCat.first,
                    name = "Hidden Draft $uniqueSuffix",
                    sku = "SF-DRF-$uniqueSuffix",
                    status = "DRAFT",
                ).also { createdProductIds += it.first }
            val archivedProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = activeCat.first,
                    name = "Hidden Archived $uniqueSuffix",
                    sku = "SF-ARC-$uniqueSuffix",
                    status = "ARCHIVED",
                ).also { createdProductIds += it.first }
            val inactiveCatActiveProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = inactiveCat.first,
                    name = "InactiveCat Active $uniqueSuffix",
                    sku = "IC-ACT-$uniqueSuffix",
                    status = "ACTIVE",
                ).also { createdProductIds += it.first }
            val inactiveCatOosProd =
                createProductViaApi(
                    staffToken = staffToken,
                    categoryId = inactiveCat.first,
                    name = "InactiveCat OOS $uniqueSuffix",
                    sku = "IC-OOS-$uniqueSuffix",
                    status = "OUT_OF_STOCK",
                ).also { createdProductIds += it.first }

            // Anonymous & Customer share the exact same storefront visibility rules and are denied from /api/admin/**
            for (actorToken in listOf(null, customerToken)) {
                val listRes =
                    performGet("/api/products", actorToken, mapOf("keyword" to uniqueSuffix))
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.result.totalElements").value(2))
                        .andReturn()
                assertEquals(setOf(activeProd.first, oosProd.first), extractItemIds(listRes))

                performGet("/api/products", actorToken, mapOf("keyword" to uniqueSuffix, "status" to "ACTIVE"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.totalElements").value(1))
                    .andExpect(jsonPath("$.result.items[0].id").value(activeProd.first))

                performGet("/api/products", actorToken, mapOf("keyword" to uniqueSuffix, "status" to "OUT_OF_STOCK"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.totalElements").value(1))
                    .andExpect(jsonPath("$.result.items[0].id").value(oosProd.first))

                for (forbiddenStatus in listOf("ALL", "DRAFT", "ARCHIVED")) {
                    performGet("/api/products", actorToken, mapOf("keyword" to uniqueSuffix, "status" to forbiddenStatus))
                        .andExpect(status().isBadRequest)
                }

                // Product detail by ID and by slug
                for (visibleKey in listOf(activeProd.first, activeProd.second, oosProd.first, oosProd.second)) {
                    performGet("/api/products/$visibleKey", actorToken)
                        .andExpect(status().isOk)
                }
                for (hiddenKey in listOf(
                    draftProd.first,
                    draftProd.second,
                    archivedProd.first,
                    archivedProd.second,
                    inactiveCatActiveProd.first,
                    inactiveCatActiveProd.second,
                    inactiveCatOosProd.first,
                    inactiveCatOosProd.second,
                )) {
                    performGet("/api/products/$hiddenKey", actorToken)
                        .andExpect(status().isNotFound)
                }

                // Public categories ignore onlyActive=false and return only active categories
                performGet("/api/categories", actorToken, mapOf("keyword" to uniqueSuffix))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.totalElements").value(1))
                    .andExpect(jsonPath("$.result.items[0].id").value(activeCat.first))

                performGet("/api/categories", actorToken, mapOf("keyword" to uniqueSuffix, "onlyActive" to "false"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.totalElements").value(1))
                    .andExpect(jsonPath("$.result.items[0].id").value(activeCat.first))

                // Category-products by ID and by slug
                for (activeCatKey in listOf(activeCat.first, activeCat.second)) {
                    val catProductsRes =
                        performGet("/api/categories/$activeCatKey/products", actorToken)
                            .andExpect(status().isOk)
                            .andExpect(jsonPath("$.result.totalElements").value(2))
                            .andReturn()
                    assertEquals(setOf(activeProd.first, oosProd.first), extractItemIds(catProductsRes))

                    for (forbiddenStatus in listOf("ALL", "DRAFT", "ARCHIVED")) {
                        performGet("/api/categories/$activeCatKey/products", actorToken, mapOf("status" to forbiddenStatus))
                            .andExpect(status().isBadRequest)
                    }
                }
                for (inactiveCatKey in listOf(inactiveCat.first, inactiveCat.second)) {
                    performGet("/api/categories/$inactiveCatKey/products", actorToken)
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.result.totalElements").value(0))
                }

                // Admin endpoints reject anonymous with 401 and customer without PRODUCT_WRITE with 403
                val expectedAdminStatus = if (actorToken == null) status().isUnauthorized else status().isForbidden
                for (adminPath in listOf(
                    "/api/admin/products",
                    "/api/admin/products/${draftProd.first}",
                    "/api/admin/categories",
                    "/api/admin/categories/${inactiveCat.first}/products",
                )) {
                    performGet(adminPath, actorToken).andExpect(expectedAdminStatus)
                }
            }

            // Staff with PRODUCT_WRITE can read all statuses and inactive categories via /api/admin/**
            val adminAllProducts =
                performGet("/api/admin/products", staffToken, mapOf("keyword" to uniqueSuffix))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("ADMIN_PRODUCT_LIST_SUCCESS"))
                    .andExpect(jsonPath("$.result.totalElements").value(6))
                    .andReturn()
            assertEquals(createdProductIds.toSet(), extractItemIds(adminAllProducts))

            performGet("/api/admin/products", staffToken, mapOf("keyword" to uniqueSuffix, "status" to "ALL"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(6))

            performGet("/api/admin/products", staffToken, mapOf("keyword" to uniqueSuffix, "status" to "DRAFT"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(draftProd.first))

            performGet("/api/admin/products", staffToken, mapOf("keyword" to uniqueSuffix, "status" to "ARCHIVED"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(archivedProd.first))

            for (adminKey in listOf(
                draftProd.first,
                draftProd.second,
                archivedProd.first,
                archivedProd.second,
                inactiveCatActiveProd.first,
                inactiveCatActiveProd.second,
                inactiveCatOosProd.first,
                inactiveCatOosProd.second,
            )) {
                performGet("/api/admin/products/$adminKey", staffToken)
                    .andExpect(status().isOk)
            }

            performGet("/api/admin/categories", staffToken, mapOf("keyword" to uniqueSuffix))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("ADMIN_CATEGORY_LIST_SUCCESS"))
                .andExpect(jsonPath("$.result.totalElements").value(2))

            performGet("/api/admin/categories", staffToken, mapOf("keyword" to uniqueSuffix, "isActive" to "false"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(inactiveCat.first))

            for (inactiveCatKey in listOf(inactiveCat.first, inactiveCat.second)) {
                val adminInactiveCatProducts =
                    performGet("/api/admin/categories/$inactiveCatKey/products", staffToken)
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.code").value("ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS"))
                        .andExpect(jsonPath("$.result.totalElements").value(2))
                        .andReturn()
                assertEquals(
                    setOf(inactiveCatActiveProd.first, inactiveCatOosProd.first),
                    extractItemIds(adminInactiveCatProducts),
                )
            }
            for (activeCatKey in listOf(activeCat.first, activeCat.second)) {
                performGet("/api/admin/categories/$activeCatKey/products", staffToken)
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result.totalElements").value(4))
            }
        } finally {
            SecurityContextHolder.clearContext()
            createdProductIds.forEach { productId ->
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
            }
            createdCategoryIds.forEach { categoryId ->
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId)
            }
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `price bounds decimal precision query filters and type mismatches return consistent 400 ErrorResponse`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            val staffToken = issuePersistedUserToken(staffRole, createdUserIds)
            val (categoryId, categorySlug) =
                createCategoryViaApi(
                    staffToken = staffToken,
                    name = "Bounds Cat $uniqueSuffix",
                    displayOrder = 1,
                    isActive = true,
                ).also { createdCategoryIds += it.first }

            // 1. Valid boundary prices: 0, max DECIMAL(15, 2), and trailing zeros
            val zeroProdRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {
                                  "categoryId": "$categoryId",
                                  "name": "Zero Bound Item $uniqueSuffix",
                                  "sku": "BND-ZERO-$uniqueSuffix",
                                  "price": 0,
                                  "stockQuantity": 5,
                                  "status": "ACTIVE"
                                }
                                """.trimIndent(),
                            ),
                    ).andExpect(status().isCreated)
                    .andReturn()
            val zeroProdId = objectMapper.readTree(zeroProdRes.response.contentAsString)["result"]["id"].asString()
            createdProductIds += zeroProdId

            val maxProdRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {
                                  "categoryId": "$categoryId",
                                  "name": "Max Bound Item $uniqueSuffix",
                                  "sku": "BND-MAX-$uniqueSuffix",
                                  "price": 9999999999999.99,
                                  "stockQuantity": 1,
                                  "status": "ACTIVE"
                                }
                                """.trimIndent(),
                            ),
                    ).andExpect(status().isCreated)
                    .andReturn()
            val maxProdId = objectMapper.readTree(maxProdRes.response.contentAsString)["result"]["id"].asString()
            createdProductIds += maxProdId
            assertEquals(
                0,
                BigDecimal("9999999999999.99").compareTo(
                    jdbcTemplate.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal::class.java, maxProdId),
                ),
            )

            // Trailing zeros that preserve <= 2 non-zero decimals are accepted and stored cleanly as 49.90
            mockMvc
                .perform(
                    put("/api/products/$zeroProdId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": "$categoryId",
                              "name": "Trailing Zero Item $uniqueSuffix",
                              "sku": "BND-ZERO-$uniqueSuffix",
                              "price": 49.900,
                              "stockQuantity": 5,
                              "status": "ACTIVE",
                              "version": 0
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.price").value(49.9))

            // 2. Price out-of-range, > 2 non-zero decimals, and invalid categoryId UUID -> 400
            for (badPriceLiteral in listOf("-0.01", "10000000000000.00", "29.995", "0.001")) {
                val createBadPrice =
                    mockMvc
                        .perform(
                            post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                      "categoryId": "$categoryId",
                                      "name": "Invalid Price $uniqueSuffix",
                                      "sku": "INV-P-$uniqueSuffix",
                                      "price": $badPriceLiteral,
                                      "stockQuantity": 1
                                    }
                                    """.trimIndent(),
                                ),
                        ).andReturn()
                assertConsistentBadRequest(createBadPrice, createdProductIds)

                val updateBadPrice =
                    mockMvc
                        .perform(
                            put("/api/products/$zeroProdId")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                      "categoryId": "$categoryId",
                                      "name": "Invalid Update Price $uniqueSuffix",
                                      "sku": "BND-ZERO-$uniqueSuffix",
                                      "price": $badPriceLiteral,
                                      "stockQuantity": 5,
                                      "status": "ACTIVE",
                                      "version": 1
                                    }
                                    """.trimIndent(),
                                ),
                        ).andReturn()
                assertConsistentBadRequest(updateBadPrice)
            }

            for (badCategoryId in listOf("not-a-uuid", "1-2-3-4-5")) {
                val createBadCat =
                    mockMvc
                        .perform(
                            post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    """
                                    {
                                      "categoryId": "$badCategoryId",
                                      "name": "Bad Cat Item $uniqueSuffix",
                                      "sku": "BAD-CAT-$uniqueSuffix",
                                      "price": 10.00
                                    }
                                    """.trimIndent(),
                                ),
                        ).andReturn()
                assertConsistentBadRequest(createBadCat, createdProductIds)
            }

            // 3. Malformed JSON and wrong-type JSON body fields -> standardized 400 ErrorResponse without internal class names
            val malformedPayloads =
                listOf(
                    "{not-valid-json",
                    """{"categoryId":"$categoryId","name":"Bad Type","sku":"BT-$uniqueSuffix","price":"abc"}""",
                    """{"categoryId":"$categoryId","name":"Bool Price","sku":"BP-$uniqueSuffix","price":true}""",
                    """{"categoryId":"$categoryId","name":"Null Price","sku":"NP-$uniqueSuffix","price":null}""",
                    """{"categoryId":"$categoryId","name":"Missing Price","sku":"MP-$uniqueSuffix"}""",
                    """{"categoryId":"$categoryId","name":"Bad Stock","sku":"BS-$uniqueSuffix","price":10,"stockQuantity":"abc"}""",
                    """{"categoryId":"$categoryId","name":"Float Stock","sku":"FS-$uniqueSuffix","price":10,"stockQuantity":1.5}""",
                )
            for (rawBody in malformedPayloads) {
                val res =
                    mockMvc
                        .perform(
                            post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawBody),
                        ).andReturn()
                assertConsistentBadRequest(res, createdProductIds)
            }

            // 4. Query parameter type mismatches -> standardized 400 ErrorResponse
            val wrongTypeQueries =
                listOf(
                    get("/api/products").param("minPrice", "abc"),
                    get("/api/products").param("maxPrice", "xyz"),
                    get("/api/products").param("minRating", "five"),
                    get("/api/products").param("page", "first"),
                    get("/api/products").param("size", "large"),
                    get("/api/categories/$categorySlug/products").param("minPrice", "bad"),
                    get("/api/admin/products").param("minRating", "oops"),
                    get("/api/admin/categories").param("isActive", "maybe"),
                )
            for (req in wrongTypeQueries) {
                val res =
                    mockMvc
                        .perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"))
                        .andReturn()
                assertConsistentBadRequest(res)
            }

            // 5. Query filter domain bounds, UUID format, and categoryId + categorySlug mutual exclusion -> 400
            val invalidFilterQueries =
                listOf(
                    get("/api/products").param("minPrice", "-0.01"),
                    get("/api/products").param("maxPrice", "-1.00"),
                    get("/api/products").param("minPrice", "100").param("maxPrice", "50"),
                    get("/api/products").param("maxPrice", "10000000000000.00"),
                    get("/api/products").param("minPrice", "10.995"),
                    get("/api/products").param("minRating", "-0.01"),
                    get("/api/products").param("minRating", "5.01"),
                    get("/api/products").param("categoryId", "not-a-uuid"),
                    get("/api/products").param("categoryId", "1-2-3-4-5"),
                    get("/api/products").param("categoryId", categoryId).param("categorySlug", categorySlug),
                    get("/api/categories").param("parentId", "not-a-uuid"),
                    get("/api/categories/$categoryId/products").param("minPrice", "-5"),
                    get("/api/categories/$categorySlug/products").param("minRating", "5.5"),
                    get("/api/admin/products").param("categoryId", categoryId).param("categorySlug", categorySlug),
                    get("/api/admin/products").param("minRating", "6"),
                    get("/api/admin/categories").param("parentId", "1-2-3-4-5"),
                )
            for (req in invalidFilterQueries) {
                val res =
                    mockMvc
                        .perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"))
                        .andReturn()
                assertConsistentBadRequest(res)
            }

            // 6. Valid boundary queries -> 200 OK
            performGet(
                "/api/products",
                null,
                mapOf(
                    "categoryId" to categoryId,
                    "minPrice" to "0.000",
                    "maxPrice" to "9999999999999.990",
                    "minRating" to "0",
                ),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(2))

            performGet(
                "/api/products",
                null,
                mapOf(
                    "categorySlug" to categorySlug,
                    "minPrice" to "49.900",
                    "maxPrice" to "49.90",
                ),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(zeroProdId))

            performGet(
                "/api/admin/products",
                staffToken,
                mapOf("categoryId" to categoryId, "minRating" to "0.00"),
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.totalElements").value(2))
        } finally {
            SecurityContextHolder.clearContext()
            createdProductIds.forEach { productId ->
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
            }
            createdCategoryIds.forEach { categoryId ->
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId)
            }
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `product lifecycle transitions and persistence mapper preserve identity auditing and concurrency`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val staffActorA = UserId.generate()
        val staffActorB = UserId.generate()
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            val staffTokenA = issuePersistedUserToken(staffRole, createdUserIds, staffActorA)
            val staffTokenB = issuePersistedUserToken(staffRole, createdUserIds, staffActorB)
            val (categoryId, _) =
                createCategoryViaApi(
                    staffToken = staffTokenA,
                    name = "Lifecycle E2E Cat $uniqueSuffix",
                    displayOrder = 1,
                    isActive = true,
                ).also { createdCategoryIds += it.first }

            // 1. Staff A creates product in DRAFT (version=0, hidden from storefront)
            val createdProd =
                createProductViaApi(
                    staffToken = staffTokenA,
                    categoryId = categoryId,
                    name = "Lifecycle Item $uniqueSuffix",
                    sku = "LCE2E-$uniqueSuffix",
                    status = "DRAFT",
                ).also { createdProductIds += it.first }
            val productId = createdProd.first

            performGet("/api/products/$productId", null)
                .andExpect(status().isNotFound)

            val initialDbRow =
                jdbcTemplate.queryForMap(
                    "SELECT id, status, version, created_at, created_by, updated_by FROM products WHERE id = ?",
                    productId,
                )
            assertEquals("DRAFT", initialDbRow["status"])
            assertEquals(0L, (initialDbRow["version"] as Number).toLong())
            assertEquals(staffActorA.value.toString(), initialDbRow["created_by"])
            assertEquals(staffActorA.value.toString(), initialDbRow["updated_by"])

            // 2. Staff B updates catalog details WITHOUT status -> remains DRAFT, created_at/created_by preserved, updated_by changed
            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenB")
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": "$categoryId",
                              "name": "Lifecycle Item Revised $uniqueSuffix",
                              "sku": "LCE2E-$uniqueSuffix",
                              "price": 59.99,
                              "stockQuantity": 20
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.version").value(1))

            val afterUpdateRow =
                jdbcTemplate.queryForMap(
                    "SELECT id, status, version, created_at, created_by, updated_by FROM products WHERE id = ?",
                    productId,
                )
            assertEquals(productId, afterUpdateRow["id"])
            assertEquals(initialDbRow["created_at"], afterUpdateRow["created_at"])
            assertEquals(staffActorA.value.toString(), afterUpdateRow["created_by"])
            assertEquals(staffActorB.value.toString(), afterUpdateRow["updated_by"])
            assertEquals("DRAFT", afterUpdateRow["status"])
            assertEquals(1L, (afterUpdateRow["version"] as Number).toLong())

            // 3. Publish explicitly via POST /{id}/publish -> ACTIVE (version=2) and visible on storefront
            mockMvc
                .perform(
                    post("/api/products/$productId/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenA")
                        .header(HttpHeaders.IF_MATCH, "\"1\""),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.code").value("PRODUCT_PUBLISH_SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.version").value(2))

            performGet("/api/products/$productId", null)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))

            // 4. Archive -> version=3; idempotent re-archive -> 204 without bumping version=4
            mockMvc
                .perform(
                    delete("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenA")
                        .header(HttpHeaders.IF_MATCH, "\"2\""),
                ).andExpect(status().isNoContent)

            mockMvc
                .perform(
                    delete("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenA")
                        .header(HttpHeaders.IF_MATCH, "\"3\""),
                ).andExpect(status().isNoContent)

            assertEquals(
                3L,
                jdbcTemplate.queryForObject("SELECT version FROM products WHERE id = ?", Long::class.java, productId),
            )

            // 5. Updating or directly publishing an ARCHIVED product is rejected with 400 Bad Request
            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenB")
                        .header(HttpHeaders.IF_MATCH, "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": "$categoryId",
                              "name": "Illegal Reactivation $uniqueSuffix",
                              "sku": "LCE2E-$uniqueSuffix",
                              "price": 79.99,
                              "stockQuantity": 15,
                              "status": "ACTIVE"
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isBadRequest)

            mockMvc
                .perform(
                    post("/api/products/$productId/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenB")
                        .header(HttpHeaders.IF_MATCH, "\"3\""),
                ).andExpect(status().isBadRequest)

            val archivedRow =
                jdbcTemplate.queryForMap("SELECT status, version FROM products WHERE id = ?", productId)
            assertEquals("ARCHIVED", archivedRow["status"])
            assertEquals(3L, (archivedRow["version"] as Number).toLong())

            // 6. Stale version on restore returns 409; valid restore moves ARCHIVED -> DRAFT (version=4)
            mockMvc
                .perform(
                    post("/api/products/$productId/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenA")
                        .header(HttpHeaders.IF_MATCH, "\"2\""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("PRODUCT_CONCURRENCY_CONFLICT"))

            mockMvc
                .perform(
                    post("/api/products/$productId/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenA")
                        .header(HttpHeaders.IF_MATCH, "\"3\""),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                .andExpect(jsonPath("$.code").value("PRODUCT_RESTORE_SUCCESS"))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.version").value(4))

            performGet("/api/products/$productId", null)
                .andExpect(status().isNotFound)

            // 7. Update DRAFT details (version 4->5) then publish (version 5->6)
            mockMvc
                .perform(
                    put("/api/products/$productId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenB")
                        .header(HttpHeaders.IF_MATCH, "\"4\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": "$categoryId",
                              "name": "Lifecycle Item Restored $uniqueSuffix",
                              "sku": "LCE2E-$uniqueSuffix",
                              "price": 69.99,
                              "stockQuantity": 25
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.version").value(5))

            mockMvc
                .perform(
                    post("/api/products/$productId/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffTokenB")
                        .header(HttpHeaders.IF_MATCH, "\"5\""),
                ).andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.ETAG, "\"6\""))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.version").value(6))

            val finalRow =
                jdbcTemplate.queryForMap(
                    "SELECT id, status, version, created_at, created_by, updated_by FROM products WHERE id = ?",
                    productId,
                )
            assertEquals(productId, finalRow["id"])
            assertEquals(initialDbRow["created_at"], finalRow["created_at"])
            assertEquals(staffActorA.value.toString(), finalRow["created_by"])
            assertEquals(staffActorB.value.toString(), finalRow["updated_by"])
            assertEquals("ACTIVE", finalRow["status"])
            assertEquals(6L, (finalRow["version"] as Number).toLong())
        } finally {
            SecurityContextHolder.clearContext()
            createdProductIds.forEach { productId ->
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", productId)
            }
            createdCategoryIds.forEach { categoryId ->
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId)
            }
            cleanupUsers(createdUserIds)
        }
    }

    private fun issuePersistedUserToken(
        role: Role,
        createdUserIds: MutableList<String>,
        userId: UserId = UserId.generate(),
    ): String {
        val now = Instant.now()
        val savedUser =
            userRepository.save(
                User(
                    id = userId,
                    email = Email.of("e2e-actor-${userId.value}@shopflow.org"),
                    hashedPassword = HashedPassword("e2e-unused-password-hash"),
                    roles = setOf(role),
                    status = UserStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        createdUserIds += savedUser.id.value.toString()
        return tokenProvider.generateAccessToken(
            userId = savedUser.id,
            roles = setOf(role.name),
            permissions = role.getPermissionCodes(),
        )
    }

    private fun cleanupUsers(userIds: List<String>) {
        userIds.distinct().forEach { userId ->
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
        }
    }

    private fun assertConsistentBadRequest(
        result: MvcResult,
        createdProductIds: MutableList<String>? = null,
    ) {
        val status = result.response.status
        val body = result.response.contentAsString
        if (status == 201 && createdProductIds != null) {
            runCatching { objectMapper.readTree(body)["result"]?.get("id")?.asString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { createdProductIds += it }
        }
        assertEquals(400, status, "Expected 400 Bad Request but got $status: $body")
        val json = objectMapper.readTree(body)
        assertEquals(400, json["status"].asInt(), "Expected status=400 in ApiResponse: $body")
        assertTrue(json["code"].asString().isNotBlank(), "Expected code in ApiResponse: $body")
        assertTrue(json["result"].isNull, "Expected result=null in ApiResponse: $body")
        assertTrue(json["message"].asString().isNotBlank(), "Expected non-blank message: $body")
        assertTrue(json["errors"].isArray && json["errors"].size() >= 1, "Expected non-empty errors array: $body")
        assertTrue(json["errors"][0]["code"].asString().isNotBlank(), "Expected error detail code: $body")
        assertTrue(json["timestamp"].asString().isNotBlank(), "Expected timestamp: $body")
        val lower = body.lowercase()
        assertFalse(lower.contains("org.shopflow"), "Response leaked internal package name: $body")
        assertFalse(lower.contains("tools.jackson"), "Response leaked Jackson class name: $body")
        assertFalse(lower.contains("java."), "Response leaked Java class name: $body")
    }

    private fun createCategoryViaApi(
        staffToken: String,
        name: String,
        displayOrder: Int,
        isActive: Boolean,
    ): Pair<String, String> {
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
                                    "displayOrder" to displayOrder,
                                    "isActive" to isActive,
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)["result"]
        return json["id"].asString() to json["slug"].asString()
    }

    private fun createProductViaApi(
        staffToken: String,
        categoryId: String,
        name: String,
        sku: String,
        status: String,
    ): Pair<String, String> {
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
                                    "price" to 49.99,
                                    "stockQuantity" to if (status == "OUT_OF_STOCK") 0 else 10,
                                    "status" to status,
                                ),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)["result"]
        return json["id"].asString() to json["slug"].asString()
    }

    private fun performGet(
        path: String,
        token: String?,
        params: Map<String, String> = emptyMap(),
    ) = mockMvc.perform(
        get(path).apply {
            if (token != null) {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }
            params.forEach { (key, value) -> param(key, value) }
        },
    )

    private fun extractItemIds(result: MvcResult): Set<String> =
        objectMapper
            .readTree(result.response.contentAsString)["result"]["items"]
            .mapTo(hashSetOf()) { it["id"].asString() }
}
