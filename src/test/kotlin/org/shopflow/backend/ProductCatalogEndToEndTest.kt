package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
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

        val customerToken =
            tokenProvider.generateAccessToken(
                userId = UserId.generate(),
                roles = setOf(customerRole.name),
                permissions = customerRole.getPermissionCodes(),
            )
        val staffActorId = UserId.generate()
        val staffToken =
            tokenProvider.generateAccessToken(
                userId = staffActorId,
                roles = setOf(staffRole.name),
                permissions = staffRole.getPermissionCodes(),
            )

        var createdCategoryId: String? = null
        var createdProductId: String? = null

        try {
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
                    .andExpect(jsonPath("$.name").value("E2E Gadgets $uniqueSuffix"))
                    .andReturn()

            createdCategoryId = objectMapper.readTree(catResult.response.contentAsString)["id"].asString()

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

            val prodResult =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createProductPayload),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.price").value(89.99))
                    .andExpect(jsonPath("$.stockQuantity").value(40))
                    .andReturn()

            val prodJson = objectMapper.readTree(prodResult.response.contentAsString)
            createdProductId = prodJson["id"].asString()
            val productSlug = prodJson["slug"].asString()

            mockMvc
                .perform(get("/api/products/$productSlug"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(createdProductId))
                .andExpect(jsonPath("$.categoryName").value("E2E Gadgets $uniqueSuffix"))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("keyword", uniqueSuffix)
                        .param("sortBy", "price_asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].id").value(createdProductId))

            val updatePayload =
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

            mockMvc
                .perform(
                    put("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Wireless Keyboard Pro $uniqueSuffix"))
                .andExpect(jsonPath("$.price").value(99.5))

            val auditRow =
                jdbcTemplate.queryForMap(
                    "SELECT created_by, updated_by, price FROM products WHERE id = ?",
                    createdProductId,
                )
            assertEquals(staffActorId.value.toString(), auditRow["created_by"])
            assertEquals(staffActorId.value.toString(), auditRow["updated_by"])
            assertNotNull(auditRow["price"])

            mockMvc
                .perform(
                    delete("/api/products/$createdProductId")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                ).andExpect(status().isNoContent)

            mockMvc
                .perform(get("/api/products/$createdProductId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
        } finally {
            SecurityContextHolder.clearContext()
            if (createdProductId != null) {
                jdbcTemplate.update("DELETE FROM products WHERE id = ?", createdProductId)
            }
            if (createdCategoryId != null) {
                jdbcTemplate.update("DELETE FROM categories WHERE id = ?", createdCategoryId)
            }
        }
    }

    @Test
    fun `pagination sorting and whitelist validation work across products and categories GET endpoints`() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val staffToken =
            tokenProvider.generateAccessToken(
                userId = UserId.generate(),
                roles = setOf(staffRole.name),
                permissions = staffRole.getPermissionCodes(),
            )

        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
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
            val alphaCatJson = objectMapper.readTree(alphaCatRes.response.contentAsString)
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
            createdCategoryIds += objectMapper.readTree(betaCatRes.response.contentAsString)["id"].asString()

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
                createdProductIds += objectMapper.readTree(res.response.contentAsString)["id"].asString()
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
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].price").value(19.99))
                .andExpect(jsonPath("$.items[1].price").value(59.99))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortBy", "price")
                        .param("sortDir", "asc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].price").value(99.99))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "price")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].price").value(99.99))
                .andExpect(jsonPath("$.items[1].price").value(59.99))
                .andExpect(jsonPath("$.items[2].price").value(19.99))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "name")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].name").value("Charlie Item $uniqueSuffix"))
                .andExpect(jsonPath("$.items[1].name").value("Bravo Item $uniqueSuffix"))
                .andExpect(jsonPath("$.items[2].name").value("Alpha Item $uniqueSuffix"))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.items[0].id").value(charlieId))
                .andExpect(jsonPath("$.items[1].id").value(alphaId))
                .andExpect(jsonPath("$.items[2].id").value(bravoId))

            mockMvc
                .perform(
                    get("/api/products")
                        .param("categoryId", alphaCatId)
                        .param("size", "500"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.size").value(100))

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
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].price").value(19.99))

            mockMvc
                .perform(
                    get("/api/categories")
                        .param("keyword", uniqueSuffix)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "name")
                        .param("sortDir", "desc"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.items[0].name").value("Beta Cat $uniqueSuffix"))

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
        }
    }
}
