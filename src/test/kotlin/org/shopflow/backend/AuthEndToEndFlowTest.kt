package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@SpringBootTest
@Import(AuthEndToEndFlowTest.ProtectedAdminProbeController::class)
class AuthEndToEndFlowTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
    fun `complete HTTP auth lifecycle register login me refresh anti-reuse forbidden and logout`() {
        val testEmail = "e2e-${UUID.randomUUID()}@shopflow.org"
        val rawPassword = "Password123!"
        var registeredUserId: String? = null

        try {
            // 1. Register new customer account -> 201 Created
            val registerPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "email" to testEmail,
                        "password" to rawPassword,
                    ),
                )
            val registerResult =
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerPayload),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.code").value("AUTH_REGISTER_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.result.email").value(testEmail))
                    .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                    .andReturn()

            val registerJson = objectMapper.readTree(registerResult.response.contentAsString)["result"]
            registeredUserId = registerJson["id"].asString()
            assertTrue(registeredUserId.isNotBlank())
            val registeredRoles = registerJson["roles"].mapTo(linkedSetOf()) { it.asString() }
            assertEquals(setOf(Role.ROLE_CUSTOMER), registeredRoles)

            // 2. Login -> 200 OK with accessToken + refreshToken
            val loginPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "email" to testEmail,
                        "password" to rawPassword,
                    ),
                )
            val loginResult =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andReturn()

            val loginJson = objectMapper.readTree(loginResult.response.contentAsString)["result"]
            val accessToken = loginJson["accessToken"].asString()
            val refreshToken = loginJson["refreshToken"].asString()
            assertTrue(accessToken.isNotBlank())
            assertTrue(refreshToken.isNotBlank())

            // 3. Query current user profile via GET /api/auth/me -> 200 OK with ROLE_CUSTOMER & permissions
            mockMvc
                .perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))

            val meResult =
                mockMvc
                    .perform(
                        get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("AUTH_ME_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.result.id").value(registeredUserId))
                    .andExpect(jsonPath("$.result.email").value(testEmail))
                    .andReturn()

            val meJson = objectMapper.readTree(meResult.response.contentAsString)["result"]
            val meRoles = meJson["roles"].mapTo(linkedSetOf()) { it.asString() }
            val mePermissions = meJson["permissions"].mapTo(linkedSetOf()) { it.asString() }
            assertEquals(setOf(Role.ROLE_CUSTOMER), meRoles)
            assertEquals(
                setOf(
                    Permission.USER_READ,
                    Permission.PRODUCT_READ,
                    Permission.ORDER_CREATE,
                    Permission.ORDER_READ_OWN,
                ),
                mePermissions,
            )

            // 3.5. Update current user profile via PUT /api/auth/me/profile -> 401 unauthenticated, 400 validation, 200 OK + auditing
            val updateProfilePayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "fullName" to "  Nguyen Van A  ",
                        "phoneNumber" to "0901234567",
                        "city" to "Ho Chi Minh",
                        "address" to "123 Le Loi, District 1",
                        "avatarUrl" to "https://cdn.shopflow.org/avatars/u1.png",
                    ),
                )
            mockMvc
                .perform(
                    put("/api/auth/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfilePayload),
                ).andExpect(status().isUnauthorized)

            val invalidProfilePayload =
                objectMapper.writeValueAsString(
                    mapOf("fullName" to "A".repeat(151)),
                )
            mockMvc
                .perform(
                    put("/api/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidProfilePayload),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"))

            mockMvc
                .perform(
                    put("/api/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfilePayload),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("AUTH_PROFILE_UPDATE_SUCCESS"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.result.id").value(registeredUserId))
                .andExpect(jsonPath("$.result.email").value(testEmail))
                .andExpect(jsonPath("$.result.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.result.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.result.city").value("Ho Chi Minh"))
                .andExpect(jsonPath("$.result.address").value("123 Le Loi, District 1"))
                .andExpect(jsonPath("$.result.avatarUrl").value("https://cdn.shopflow.org/avatars/u1.png"))

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.result.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.result.city").value("Ho Chi Minh"))
                .andExpect(jsonPath("$.result.address").value("123 Le Loi, District 1"))
                .andExpect(jsonPath("$.result.avatarUrl").value("https://cdn.shopflow.org/avatars/u1.png"))

            val dbProfileAudit =
                jdbcTemplate.queryForMap(
                    "SELECT full_name, phone_number, city, address, avatar_url, created_by, updated_by FROM users WHERE id = ?",
                    registeredUserId,
                )
            assertEquals("Nguyen Van A", dbProfileAudit["full_name"])
            assertEquals("0901234567", dbProfileAudit["phone_number"])
            assertEquals("Ho Chi Minh", dbProfileAudit["city"])
            assertEquals("123 Le Loi, District 1", dbProfileAudit["address"])
            assertEquals("https://cdn.shopflow.org/avatars/u1.png", dbProfileAudit["avatar_url"])
            assertEquals("SYSTEM", dbProfileAudit["created_by"])
            assertEquals(registeredUserId, dbProfileAudit["updated_by"])

            // 4. Refresh token rotation -> 200 OK & old refresh token becomes invalid (anti-reuse -> 401)
            val refreshPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
            val refreshResult =
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshPayload),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("AUTH_REFRESH_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andReturn()

            val refreshedJson = objectMapper.readTree(refreshResult.response.contentAsString)["result"]
            val rotatedAccessToken = refreshedJson["accessToken"].asString()
            val rotatedRefreshToken = refreshedJson["refreshToken"].asString()
            assertTrue(rotatedAccessToken.isNotBlank())
            assertTrue(rotatedRefreshToken.isNotBlank())
            assertNotEquals(refreshToken, rotatedRefreshToken)

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.message").isNotEmpty)

            // 5. Call Staff/Admin protected endpoints with ROLE_CUSTOMER token -> 403 Forbidden ApiResponse
            mockMvc
                .perform(
                    get("/api/test-e2e-protected/staff-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $rotatedAccessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").isNotEmpty)
                .andExpect(jsonPath("$.errors[0].code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty)

            mockMvc
                .perform(
                    get("/api/test-e2e-protected/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $rotatedAccessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").isNotEmpty)
                .andExpect(jsonPath("$.timestamp").isNotEmpty)

            // 6. Logout revokes rotated refresh token -> 204 No Content & empty body & subsequent refresh fails with 401
            val logoutPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to rotatedRefreshToken))
            val logoutRes =
                mockMvc
                    .perform(
                        post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(logoutPayload),
                    ).andExpect(status().isNoContent)
                    .andReturn()
            assertEquals("", logoutRes.response.contentAsString)

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutPayload),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
        } finally {
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    @Test
    fun `consecutive logins and immediate refresh do not hit unique constraint and reject rotated token`() {
        val testEmail = "rapid-refresh-${UUID.randomUUID()}@shopflow.org"
        val rawPassword = "Password123!"
        var registeredUserId: String? = null

        try {
            val registerPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "email" to testEmail,
                        "password" to rawPassword,
                    ),
                )
            val registerResult =
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerPayload),
                    ).andExpect(status().isCreated)
                    .andReturn()
            registeredUserId = objectMapper.readTree(registerResult.response.contentAsString)["result"]["id"].asString()

            val loginPayload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "email" to testEmail,
                        "password" to rawPassword,
                    ),
                )
            val login1Result =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload),
                    ).andExpect(status().isOk)
                    .andReturn()
            val login2Result =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload),
                    ).andExpect(status().isOk)
                    .andReturn()

            val refreshToken1 = objectMapper.readTree(login1Result.response.contentAsString)["result"]["refreshToken"].asString()
            val refreshToken2 = objectMapper.readTree(login2Result.response.contentAsString)["result"]["refreshToken"].asString()
            assertTrue(refreshToken1.isNotBlank())
            assertTrue(refreshToken2.isNotBlank())
            assertNotEquals(refreshToken1, refreshToken2)

            val tokenCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                    Int::class.java,
                    registeredUserId,
                ) ?: 0
            assertEquals(2, tokenCount)

            val refresh2Payload = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken2))
            val rotated2Result =
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refresh2Payload),
                    ).andExpect(status().isOk)
                    .andReturn()
            val rotatedRefreshToken =
                objectMapper.readTree(rotated2Result.response.contentAsString)["result"]["refreshToken"].asString()
            assertTrue(rotatedRefreshToken.isNotBlank())
            assertNotEquals(refreshToken2, rotatedRefreshToken)
            assertNotEquals(refreshToken1, rotatedRefreshToken)

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refresh2Payload),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))

            val refresh1Payload = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken1))
            val rotated1Result =
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refresh1Payload),
                    ).andExpect(status().isOk)
                    .andReturn()
            val rotatedFirstSessionToken =
                objectMapper.readTree(rotated1Result.response.contentAsString)["result"]["refreshToken"].asString()
            assertNotEquals(refreshToken1, rotatedFirstSessionToken)

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refresh1Payload),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
        } finally {
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    @Test
    fun `register accepts optional fullName and city for dataset compatibility`() {
        val testEmail = "dataset-${UUID.randomUUID()}@shopflow.org"
        var registeredUserId: String? = null

        try {
            val payload =
                objectMapper.writeValueAsString(
                    mapOf(
                        "email" to testEmail,
                        "password" to "Password123!",
                        "fullName" to "  Tran Thi B ",
                        "city" to "Da Nang",
                    ),
                )
            val result =
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.code").value("AUTH_REGISTER_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.result.email").value(testEmail))
                    .andExpect(jsonPath("$.result.fullName").value("Tran Thi B"))
                    .andExpect(jsonPath("$.result.city").value("Da Nang"))
                    .andReturn()

            registeredUserId = objectMapper.readTree(result.response.contentAsString)["result"]["id"].asString()
            val dbRow =
                jdbcTemplate.queryForMap(
                    "SELECT full_name, city, created_by FROM users WHERE id = ?",
                    registeredUserId,
                )
            assertEquals("Tran Thi B", dbRow["full_name"])
            assertEquals("Da Nang", dbRow["city"])
            assertEquals("SYSTEM", dbRow["created_by"])
        } finally {
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    private fun cleanupTestUser(
        registeredUserId: String?,
        email: String,
    ) {
        SecurityContextHolder.clearContext()
        val userIdToDelete =
            registeredUserId ?: jdbcTemplate
                .query(
                    "SELECT id FROM users WHERE email = ?",
                    { rs, _ -> rs.getString("id") },
                    email,
                ).firstOrNull()
        if (userIdToDelete != null) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userIdToDelete)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userIdToDelete)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userIdToDelete)
        }
    }

    @RestController
    @RequestMapping("/api/test-e2e-protected")
    class ProtectedAdminProbeController {
        @GetMapping("/staff-products")
        @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
        fun staffProducts(): Map<String, String> = mapOf("status" to "allowed")

        @GetMapping("/admin-users")
        @PreAuthorize("hasRole('ADMIN')")
        fun adminUsers(): Map<String, String> = mapOf("status" to "allowed")
    }
}
