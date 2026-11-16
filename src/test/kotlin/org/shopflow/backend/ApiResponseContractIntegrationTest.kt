package org.shopflow.backend

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Valid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.domain.service.auth.UserSecuritySnapshot
import org.shopflow.backend.domain.service.auth.UserSecurityStatePort
import org.shopflow.backend.infrastructure.security.jwt.JwtAuthFilter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.HandlerExceptionResolver
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

@SpringBootTest
@Import(ApiResponseContractIntegrationTest.ContractProbeController::class)
class ApiResponseContractIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private lateinit var handlerExceptionResolver: HandlerExceptionResolver

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        assertEquals("shopflow_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
    }

    @Test
    fun `status, auth, catalog, pagination, and 204 endpoints satisfy unified success envelope without double wrapping`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            // Root plain-text endpoint is intentionally NOT wrapped in JSON
            mockMvc
                .perform(get("/"))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith(MediaType.TEXT_PLAIN_VALUE)))
                .andExpect { result ->
                    assertEquals("Everything cool!", result.response.contentAsString)
                }

            val statusRes =
                mockMvc
                    .perform(get("/api/status"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("SYSTEM_STATUS_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.result.status").value("UP"))
                    .andExpect(jsonPath("$.result.timestamp").isNotEmpty)
                    .andReturn()
            assertSuccessEnvelope(statusRes, 200)

            // Register 201 + Login 200
            val customerEmail = "contract-cust-$suffix@shopflow.org"
            val customerPassword = "Password123!"
            val registerRes =
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("email" to customerEmail, "password" to customerPassword))),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.code").value("AUTH_REGISTER_SUCCESS"))
                    .andReturn()
            val regJson = assertSuccessEnvelope(registerRes, 201)
            createdUserIds += regJson["result"]["id"].asString()

            val loginRes =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("email" to customerEmail, "password" to customerPassword))),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                    .andReturn()
            val loginJson = assertSuccessEnvelope(loginRes, 200)
            val refreshToken = loginJson["result"]["refreshToken"].asString()

            // Logout 204 has empty body
            val logoutRes =
                mockMvc
                    .perform(
                        post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                    ).andExpect(status().isNoContent)
                    .andReturn()
            assertEquals("", logoutRes.response.contentAsString)

            // Staff catalog create + detail + paged list + delete 204
            val staffToken = createPersistedToken(Role.ROLE_STAFF, createdUserIds)
            val catRes =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("name" to "Contract Cat $suffix", "displayOrder" to 1))),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.code").value("CATEGORY_CREATE_SUCCESS"))
                    .andReturn()
            val categoryId = assertSuccessEnvelope(catRes, 201)["result"]["id"].asString().also { createdCategoryIds += it }

            val createProdRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "categoryId" to categoryId,
                                        "name" to "Contract Prod $suffix",
                                        "sku" to "CTR-$suffix",
                                        "price" to 39.99,
                                        "stockQuantity" to 12,
                                        "status" to "ACTIVE",
                                    ),
                                ),
                            ),
                    ).andExpect(status().isCreated)
                    .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                    .andExpect(jsonPath("$.code").value("PRODUCT_CREATE_SUCCESS"))
                    .andReturn()
            val productId = assertSuccessEnvelope(createProdRes, 201)["result"]["id"].asString().also { createdProductIds += it }

            val detailRes =
                mockMvc
                    .perform(get("/api/products/$productId"))
                    .andExpect(status().isOk)
                    .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                    .andExpect(jsonPath("$.code").value("PRODUCT_DETAIL_SUCCESS"))
                    .andExpect(jsonPath("$.result.id").value(productId))
                    .andReturn()
            assertSuccessEnvelope(detailRes, 200)

            val listRes =
                mockMvc
                    .perform(get("/api/products").param("categoryId", categoryId))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("PRODUCT_LIST_SUCCESS"))
                    .andExpect(jsonPath("$.result.items[0].id").value(productId))
                    .andExpect(jsonPath("$.result.page").value(0))
                    .andExpect(jsonPath("$.result.size").value(20))
                    .andExpect(jsonPath("$.result.totalElements").value(1))
                    .andExpect(jsonPath("$.result.totalPages").value(1))
                    .andExpect(jsonPath("$.result.hasNext").value(false))
                    .andExpect(jsonPath("$.result.hasPrevious").value(false))
                    .andReturn()
            val listJson = assertSuccessEnvelope(listRes, 200)
            assertFalse(listJson["result"].has("result"), "Must not double-wrap result")

            val deleteRes =
                mockMvc
                    .perform(
                        delete("/api/products/$productId")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .header(HttpHeaders.IF_MATCH, "\"0\""),
                    ).andExpect(status().isNoContent)
                    .andReturn()
            assertEquals("", deleteRes.response.contentAsString)
        } finally {
            cleanupCatalog(createdProductIds, createdCategoryIds)
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `field validation, object validation, domain 404 409, and sensitive value redaction follow error contract`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val createdUserIds = mutableListOf<String>()
        val createdCategoryIds = mutableListOf<String>()
        val createdProductIds = mutableListOf<String>()

        try {
            val staffToken = createPersistedToken(Role.ROLE_STAFF, createdUserIds)

            // Password validation failure must expose field + code=INVALID_PASSWORD and never echo rejected password value
            val secretBadPassword = "weak_secret_$suffix"
            val badPassRes =
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf("email" to "valid-$suffix@shopflow.org", "password" to secretBadPassword),
                                ),
                            ),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("password"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"))
                    .andReturn()
            assertErrorEnvelope(badPassRes, 400)
            assertFalse(badPassRes.response.contentAsString.contains(secretBadPassword))

            // Price validation failure exposes field=price and code=INVALID_PRICE
            val badPriceRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "categoryId" to UUID.randomUUID().toString(),
                                        "name" to "Bad Price",
                                        "sku" to "BP-$suffix",
                                        "price" to -10.50,
                                        "stockQuantity" to 1,
                                    ),
                                ),
                            ),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("price"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PRICE"))
                    .andReturn()
            assertErrorEnvelope(badPriceRes, 400)

            // Object-level Bean Validation error serializes field=null cleanly
            val objectErrRes =
                mockMvc
                    .perform(
                        post("/api/test-contract/range")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"minValue":50,"maxValue":10}"""),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_OBJECT"))
                    .andExpect(jsonPath("$.errors[0].message").value("minValue must not exceed maxValue."))
                    .andReturn()
            val objErrJson = assertErrorEnvelope(objectErrRes, 400)
            assertTrue(objErrJson["errors"][0].has("field"))
            assertTrue(objErrJson["errors"][0]["field"].isNull)

            // Business 404 (PRODUCT_NOT_FOUND & CATEGORY_NOT_FOUND)
            val missingProdRes =
                mockMvc
                    .perform(get("/api/products/${UUID.randomUUID()}"))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                    .andReturn()
            assertErrorEnvelope(missingProdRes, 404)

            val missingCatProdRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "categoryId" to UUID.randomUUID().toString(),
                                        "name" to "Missing Cat",
                                        "sku" to "MC-$suffix",
                                        "price" to 12.00,
                                        "stockQuantity" to 1,
                                    ),
                                ),
                            ),
                    ).andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                    .andReturn()
            assertErrorEnvelope(missingCatProdRes, 404)

            // Business 409 (DUPLICATE_PRODUCT_SKU & USER_ALREADY_EXISTS)
            val catRes =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("name" to "Dup Cat $suffix"))),
                    ).andExpect(status().isCreated)
                    .andReturn()
            val catId = objectMapper.readTree(catRes.response.contentAsString)["result"]["id"].asString().also { createdCategoryIds += it }

            val firstProdPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "categoryId" to catId,
                        "name" to "Dup Item $suffix",
                        "sku" to "DUP-$suffix",
                        "price" to 25.00,
                        "stockQuantity" to 3,
                    ),
                )
            val firstProdRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstProdPayload),
                    ).andExpect(status().isCreated)
                    .andReturn()
            createdProductIds += objectMapper.readTree(firstProdRes.response.contentAsString)["result"]["id"].asString()

            val dupSkuRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstProdPayload),
                    ).andExpect(status().isConflict)
                    .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_SKU"))
                    .andExpect(jsonPath("$.errors[0].field").value("sku"))
                    .andReturn()
            assertErrorEnvelope(dupSkuRes, 409)
        } finally {
            cleanupCatalog(createdProductIds, createdCategoryIds)
            cleanupUsers(createdUserIds)
        }
    }

    @Test
    fun `auth errors, Spring MVC framework errors, content negotiation, and 500 handler obey contract`() {
        val createdUserIds = mutableListOf<String>()

        try {
            val customerToken = createPersistedToken(Role.ROLE_CUSTOMER, createdUserIds)
            val staffToken = createPersistedToken(Role.ROLE_STAFF, createdUserIds)

            // 1. Missing token (401 UNAUTHORIZED) vs invalid token (401 INVALID_TOKEN) vs public with bad token (200)
            val missingAuthRes =
                mockMvc
                    .perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andReturn()
            assertErrorEnvelope(missingAuthRes, 401)

            val badAuthRes =
                mockMvc
                    .perform(
                        get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"),
                    ).andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                    .andReturn()
            assertErrorEnvelope(badAuthRes, 401)

            // Public GET /api/products remains accessible even when client sends a stale/invalid Bearer token
            mockMvc
                .perform(
                    get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("PRODUCT_LIST_SUCCESS"))

            // Customer calling staff endpoint -> 403 ACCESS_DENIED
            val deniedRes =
                mockMvc
                    .perform(
                        post("/api/categories")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"Denied Cat"}"""),
                    ).andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andReturn()
            assertErrorEnvelope(deniedRes, 403)

            // Invalid credentials on login -> 401 INVALID_CREDENTIALS
            val badCredsRes =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"email":"nobody@shopflow.org","password":"WrongPassword123"}"""),
                    ).andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andReturn()
            assertErrorEnvelope(badCredsRes, 401)

            // 2. Malformed JSON, missing body, wrong JSON field type, wrong query type, missing query param, missing part
            val malformedRes =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{broken"),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                    .andReturn()
            assertErrorEnvelope(malformedRes, 400)

            val missingBodyRes =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("MISSING_REQUEST_BODY"))
                    .andReturn()
            assertErrorEnvelope(missingBodyRes, 400)

            val jsonTypeRes =
                mockMvc
                    .perform(
                        post("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"categoryId":"${UUID.randomUUID()}","name":"T","sku":"T-1","price":"abc"}"""),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("price"))
                    .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
                    .andReturn()
            assertErrorEnvelope(jsonTypeRes, 400)

            val queryTypeRes =
                mockMvc
                    .perform(get("/api/products").param("minPrice", "notANumber"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                    .andExpect(jsonPath("$.errors[0].field").value("minPrice"))
                    .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
                    .andReturn()
            assertErrorEnvelope(queryTypeRes, 400)

            val missingParamRes =
                mockMvc
                    .perform(
                        delete("/api/media")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                    .andExpect(jsonPath("$.errors[0].field").value("publicId"))
                    .andReturn()
            assertErrorEnvelope(missingParamRes, 400)

            val missingPartRes =
                mockMvc
                    .perform(
                        multipart("/api/media/upload")
                            .file(MockMultipartFile("other", "x.txt", "text/plain", "hi".toByteArray()))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                    ).andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("MISSING_MULTIPART_PART"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andReturn()
            assertErrorEnvelope(missingPartRes, 400)

            // 3. Unknown endpoint 404 ENDPOINT_NOT_FOUND (both authenticated route and unmapped public nested path), 405, 415, 406, 413
            val unknownRouteRes =
                mockMvc
                    .perform(
                        get("/api/non-existent-endpoint")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
                    ).andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"))
                    .andReturn()
            assertErrorEnvelope(unknownRouteRes, 404)

            val unknownPublicNestedRes =
                mockMvc
                    .perform(get("/api/products/unknown/nested/path"))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"))
                    .andReturn()
            assertErrorEnvelope(unknownPublicNestedRes, 404)

            val methodNotAllowedPublicRes =
                mockMvc
                    .perform(get("/api/auth/login"))
                    .andExpect(status().isMethodNotAllowed)
                    .andExpect(header().exists(HttpHeaders.ALLOW))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andReturn()
            assertErrorEnvelope(methodNotAllowedPublicRes, 405)
            assertTrue(methodNotAllowedPublicRes.response.getHeader(HttpHeaders.ALLOW)!!.contains("POST"))

            val methodNotAllowedAuthedRes =
                mockMvc
                    .perform(
                        patch("/api/products")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
                    ).andExpect(status().isMethodNotAllowed)
                    .andExpect(header().exists(HttpHeaders.ALLOW))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                    .andReturn()
            assertErrorEnvelope(methodNotAllowedAuthedRes, 405)
            assertTrue(methodNotAllowedAuthedRes.response.getHeader(HttpHeaders.ALLOW)!!.contains("GET"))

            val unsupportedMediaRes =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("email=a&password=b"),
                    ).andExpect(status().isUnsupportedMediaType)
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                    .andReturn()
            assertErrorEnvelope(unsupportedMediaRes, 415)

            val notAcceptableRes =
                mockMvc
                    .perform(
                        get("/api/products")
                            .accept(MediaType.APPLICATION_XML),
                    ).andExpect(status().isNotAcceptable)
                    .andReturn()
            assertEquals("", notAcceptableRes.response.contentAsString)

            val maxUploadRes =
                mockMvc
                    .perform(
                        get("/api/test-contract/max-upload")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                    ).andExpect(status().`is`(413))
                    .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                    .andExpect(jsonPath("$.errors[0].field").value("file"))
                    .andExpect(jsonPath("$.errors[0].code").value("FILE_TOO_LARGE"))
                    .andReturn()
            assertErrorEnvelope(maxUploadRes, 413)

            // 4. Unhandled 500 via MVC controller and via Security filter -> safe message, zero internal leakage
            val serverErrRes =
                mockMvc
                    .perform(
                        get("/api/test-contract/unexpected-error")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                    ).andExpect(status().isInternalServerError)
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                    .andReturn()
            assertErrorEnvelope(serverErrRes, 500)
            assertFalse(serverErrRes.response.contentAsString.contains("secret-db-detail"))
            assertFalse(serverErrRes.response.contentAsString.contains("IllegalStateException"))

            // Security filter failure delegates through HandlerExceptionResolverComposite to GlobalExceptionHandler -> same 500 envelope
            val failingFilter =
                JwtAuthFilter(
                    tokenProvider = tokenProvider,
                    userSecurityStatePort =
                        object : UserSecurityStatePort {
                            override fun findByUserId(userId: UserId): UserSecuritySnapshot? =
                                throw IllegalStateException("filter-secret-sql-token-leak")
                        },
                    handlerExceptionResolver = handlerExceptionResolver,
                )
            val filterReq =
                MockHttpServletRequest("GET", "/api/auth/me").apply {
                    addHeader(HttpHeaders.AUTHORIZATION, "Bearer $staffToken")
                }
            val filterResp = MockHttpServletResponse()
            failingFilter.doFilter(filterReq, filterResp, MockFilterChain())
            assertEquals(500, filterResp.status)
            val filterJson = objectMapper.readTree(filterResp.contentAsString)
            assertEquals(500, filterJson["status"].asInt())
            assertEquals("INTERNAL_SERVER_ERROR", filterJson["code"].asString())
            assertTrue(filterJson["result"].isNull)
            assertFalse(filterResp.contentAsString.contains("filter-secret-sql-token-leak"))
        } finally {
            cleanupUsers(createdUserIds)
        }
    }

    private fun assertSuccessEnvelope(
        result: MvcResult,
        expectedStatus: Int,
    ): JsonNode {
        assertEquals(expectedStatus, result.response.status)
        val json = objectMapper.readTree(result.response.contentAsString)
        assertTrue(json["code"].asString().isNotBlank())
        assertTrue(json["message"].asString().isNotBlank())
        assertEquals(expectedStatus, json["status"].asInt())
        assertTrue(json.has("result") && !json["result"].isNull)
        assertFalse(json.has("errors"))
        assertFalse(json.has("timestamp"))
        return json
    }

    private fun assertErrorEnvelope(
        result: MvcResult,
        expectedStatus: Int,
    ): JsonNode {
        assertEquals(expectedStatus, result.response.status)
        val body = result.response.contentAsString
        val json = objectMapper.readTree(body)
        assertTrue(json["code"].asString().isNotBlank(), "Expected non-blank code: $body")
        assertTrue(json["message"].asString().isNotBlank(), "Expected non-blank message: $body")
        assertEquals(expectedStatus, json["status"].asInt(), "Expected status=$expectedStatus: $body")
        assertTrue(json.has("result") && json["result"].isNull, "Expected result=null on error: $body")
        assertTrue(json["errors"].isArray && json["errors"].size() >= 1, "Expected structured errors list: $body")
        val firstErr = json["errors"][0]
        assertTrue(firstErr.has("field"))
        assertTrue(firstErr["code"].asString().isNotBlank())
        assertTrue(firstErr["message"].asString().isNotBlank())
        assertTrue(json["timestamp"].asString().isNotBlank())
        val lower = body.lowercase()
        assertFalse(lower.contains("org.shopflow"), "Leaked internal package: $body")
        assertFalse(lower.contains("tools.jackson"), "Leaked Jackson class: $body")
        assertFalse(lower.contains("java.lang."), "Leaked Java class: $body")
        return json
    }

    private fun createPersistedToken(
        roleName: String,
        createdUserIds: MutableList<String>,
    ): String {
        val role = roleRepository.findByName(roleName)!!
        val userId = UserId.generate()
        val now = Instant.now()
        userRepository.save(
            User(
                id = userId,
                email = Email.of("contract-${userId.value}@shopflow.org"),
                hashedPassword = passwordHasher.encode("Contract@123!"),
                roles = setOf(role),
                status = UserStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        createdUserIds += userId.value.toString()
        return tokenProvider.generateAccessToken(
            userId = userId,
            roles = setOf(role.name),
            permissions = role.getPermissionCodes(),
        )
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

    private fun cleanupUsers(userIds: List<String>) {
        SecurityContextHolder.clearContext()
        userIds.distinct().forEach { userId ->
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
        }
    }

    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.RUNTIME)
    @Constraint(validatedBy = [ValidRangeValidator::class])
    annotation class ValidRange(
        val message: String = "minValue must not exceed maxValue.",
        val groups: Array<KClass<*>> = [],
        val payload: Array<KClass<out Payload>> = [],
    )

    class ValidRangeValidator : ConstraintValidator<ValidRange, RangeProbeRequest> {
        override fun isValid(
            value: RangeProbeRequest,
            context: ConstraintValidatorContext,
        ): Boolean = value.minValue <= value.maxValue
    }

    @ValidRange
    data class RangeProbeRequest(
        val minValue: Int,
        val maxValue: Int,
    )

    @RestController
    @RequestMapping("/api/test-contract")
    class ContractProbeController {
        @PostMapping("/range")
        fun validateRange(
            @Valid @RequestBody body: RangeProbeRequest,
        ): Map<String, Int> = mapOf("min" to body.minValue, "max" to body.maxValue)

        @GetMapping("/max-upload")
        fun simulateMaxUpload(): String = throw MaxUploadSizeExceededException(5L * 1024L * 1024L)

        @GetMapping("/unexpected-error")
        fun simulateUnexpected(): String = throw IllegalStateException("secret-db-detail users idx_users_email")
    }

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
