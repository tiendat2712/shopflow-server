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
                    .andExpect(jsonPath("$.email").value(testEmail))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andReturn()

            val registerJson = objectMapper.readTree(registerResult.response.contentAsString)
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
                    .andReturn()

            val loginJson = objectMapper.readTree(loginResult.response.contentAsString)
            val accessToken = loginJson["accessToken"].asString()
            val refreshToken = loginJson["refreshToken"].asString()
            assertTrue(accessToken.isNotBlank())
            assertTrue(refreshToken.isNotBlank())

            // 3. Query current user profile via GET /api/auth/me -> 200 OK with ROLE_CUSTOMER & permissions
            mockMvc
                .perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized)

            val meResult =
                mockMvc
                    .perform(
                        get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(registeredUserId))
                    .andExpect(jsonPath("$.email").value(testEmail))
                    .andReturn()

            val meJson = objectMapper.readTree(meResult.response.contentAsString)
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

            mockMvc
                .perform(
                    put("/api/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfilePayload),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(registeredUserId))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.city").value("Ho Chi Minh"))
                .andExpect(jsonPath("$.address").value("123 Le Loi, District 1"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.shopflow.org/avatars/u1.png"))

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.city").value("Ho Chi Minh"))
                .andExpect(jsonPath("$.address").value("123 Le Loi, District 1"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.shopflow.org/avatars/u1.png"))

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
            // JWT numeric dates (iat/exp) have 1-second resolution per RFC 7519; advance past the second boundary
            Thread.sleep(1_100)
            val refreshPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
            val refreshResult =
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshPayload),
                    ).andExpect(status().isOk)
                    .andReturn()

            val refreshedJson = objectMapper.readTree(refreshResult.response.contentAsString)
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
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty)

            // 5. Call Staff/Admin protected endpoints with ROLE_CUSTOMER token -> 403 Forbidden ErrorResponse
            mockMvc
                .perform(
                    get("/api/test-e2e-protected/staff-products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $rotatedAccessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty)
                .andExpect(jsonPath("$.timestamp").isNotEmpty)

            mockMvc
                .perform(
                    get("/api/test-e2e-protected/admin-users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $rotatedAccessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty)
                .andExpect(jsonPath("$.timestamp").isNotEmpty)

            // 6. Logout revokes rotated refresh token -> 204 No Content & subsequent refresh fails with 401
            val logoutPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to rotatedRefreshToken))
            mockMvc
                .perform(
                    post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutPayload),
                ).andExpect(status().isNoContent)

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutPayload),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
        } finally {
            SecurityContextHolder.clearContext()
            val userIdToDelete =
                registeredUserId ?: jdbcTemplate
                    .query(
                        "SELECT id FROM users WHERE email = ?",
                        { rs, _ -> rs.getString("id") },
                        testEmail,
                    ).firstOrNull()
            if (userIdToDelete != null) {
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userIdToDelete)
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userIdToDelete)
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userIdToDelete)
            }
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
                    .andExpect(jsonPath("$.email").value(testEmail))
                    .andExpect(jsonPath("$.fullName").value("Tran Thi B"))
                    .andExpect(jsonPath("$.city").value("Da Nang"))
                    .andReturn()

            registeredUserId = objectMapper.readTree(result.response.contentAsString)["id"].asString()
            val dbRow =
                jdbcTemplate.queryForMap(
                    "SELECT full_name, city, created_by FROM users WHERE id = ?",
                    registeredUserId,
                )
            assertEquals("Tran Thi B", dbRow["full_name"])
            assertEquals("Da Nang", dbRow["city"])
            assertEquals("SYSTEM", dbRow["created_by"])
        } finally {
            val userIdToDelete =
                registeredUserId ?: jdbcTemplate
                    .query(
                        "SELECT id FROM users WHERE email = ?",
                        { rs, _ -> rs.getString("id") },
                        testEmail,
                    ).firstOrNull()
            if (userIdToDelete != null) {
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userIdToDelete)
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userIdToDelete)
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userIdToDelete)
            }
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
