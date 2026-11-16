package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.repository.auth.PermissionRepository
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(AccessTokenRevocationIntegrationTest.ProtectedProbeController::class)
class AccessTokenRevocationIntegrationTest {
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
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        SecurityContextHolder.clearContext()
        assertEquals("shopflow_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
    }

    @Test
    fun `access token issued before locking user INACTIVE or BANNED fails closed with 403 ACCOUNT_INACTIVE`() {
        val email = "lock-revocation-${UUID.randomUUID()}@shopflow.org"
        val password = "Password123!"
        var createdUserId: String? = null

        try {
            createdUserId = registerViaApi(email, password)
            val (accessToken, refreshToken) = loginViaApi(email, password)

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value("AUTH_ME_SUCCESS"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))

            // Lock user -> INACTIVE
            val userId = UserId.fromString(createdUserId)
            val current = userRepository.findById(userId)!!
            userRepository.save(current.copy(status = UserStatus.INACTIVE, updatedAt = Instant.now()))

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("User account is inactive."))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.errors[0].code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty)

            mockMvc
                .perform(
                    put("/api/auth/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("fullName" to "Locked User"))),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))

            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))

            // Stale Authorization header on permitAll storefront endpoint must still allow anonymous public access
            mockMvc
                .perform(
                    get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            // Lock user -> BANNED
            val inactive = userRepository.findById(userId)!!
            userRepository.save(inactive.copy(status = UserStatus.BANNED, updatedAt = Instant.now()))

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("User account is banned."))

            // Reactivating restores access for unexpired token
            val banned = userRepository.findById(userId)!!
            userRepository.save(banned.copy(status = UserStatus.ACTIVE, updatedAt = Instant.now()))

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
        } finally {
            cleanupUser(createdUserId, email)
        }
    }

    @Test
    fun `access token issued before deleting user returns 401 Unauthorized on authenticated endpoints`() {
        val email = "delete-revocation-${UUID.randomUUID()}@shopflow.org"
        val password = "Password123!"
        var createdUserId: String? = null

        try {
            createdUserId = registerViaApi(email, password)
            val (accessToken, _) = loginViaApi(email, password)

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            cleanupUser(createdUserId, email)

            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").isNotEmpty)

            mockMvc
                .perform(
                    get("/api/test-live-auth/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))

            mockMvc
                .perform(
                    get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
        } finally {
            cleanupUser(createdUserId, email)
        }
    }

    @Test
    fun `access token issued before revoking role from user stops granting revoked role immediately`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "revoke-role-$suffix@shopflow.org"
        val password = "Password123!"
        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val now = Instant.now()
        var savedUserId: String? = null

        try {
            val savedUser =
                userRepository.save(
                    User(
                        id = UserId.generate(),
                        email = Email.of(email),
                        hashedPassword = passwordHasher.encode(password),
                        roles = setOf(customerRole, staffRole),
                        status = UserStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            savedUserId = savedUser.id.value.toString()

            val (accessToken, _) = loginViaApi(email, password)
            assertTrue(tokenProvider.extractRoles(accessToken).contains(Role.ROLE_STAFF))
            assertTrue(tokenProvider.extractPermissions(accessToken).contains(Permission.PRODUCT_WRITE))

            mockMvc
                .perform(
                    get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            mockMvc
                .perform(
                    get("/api/test-live-auth/staff-role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            // Revoke ROLE_STAFF while leaving user ACTIVE with ROLE_CUSTOMER
            val loadedUser = userRepository.findById(savedUser.id)!!
            userRepository.save(loadedUser.copy(roles = setOf(customerRole), updatedAt = Instant.now()))

            // Very next request using same JWT (still carrying ROLE_STAFF/PRODUCT_WRITE claims) is denied
            mockMvc
                .perform(
                    get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            mockMvc
                .perform(
                    get("/api/test-live-auth/staff-role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            mockMvc
                .perform(
                    get("/api/test-live-auth/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            // Active user still retaining ROLE_CUSTOMER can access /api/auth/me and sees live authorities
            val meResult =
                mockMvc
                    .perform(
                        get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                    ).andExpect(status().isOk)
                    .andReturn()
            val meJson = objectMapper.readTree(meResult.response.contentAsString)["result"]
            val roles = meJson["roles"].mapTo(linkedSetOf()) { it.asString() }
            val permissions = meJson["permissions"].mapTo(linkedSetOf()) { it.asString() }
            assertEquals(setOf(Role.ROLE_CUSTOMER), roles)
            assertFalse(permissions.contains(Permission.PRODUCT_WRITE))
        } finally {
            cleanupUser(savedUserId, email)
        }
    }

    @Test
    fun `access token issued before revoking permission from role stops granting permission immediately`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val roleName = "ROLE_TEMP_EDITOR_${suffix.uppercase()}"
        val email = "revoke-perm-$suffix@shopflow.org"
        val password = "Password123!"
        var tempRoleId: String? = null
        var savedUserId: String? = null

        try {
            val permissions = permissionRepository.findAllByCodes(listOf(Permission.USER_READ, Permission.PRODUCT_WRITE)).toSet()
            assertEquals(2, permissions.size)

            val tempRole =
                roleRepository.save(
                    Role.create(
                        name = roleName,
                        description = "Temporary role for permission revocation test",
                        permissions = permissions,
                    ),
                )
            tempRoleId = tempRole.id.value.toString()

            val now = Instant.now()
            val savedUser =
                userRepository.save(
                    User(
                        id = UserId.generate(),
                        email = Email.of(email),
                        hashedPassword = passwordHasher.encode(password),
                        roles = setOf(tempRole),
                        status = UserStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            savedUserId = savedUser.id.value.toString()

            val (accessToken, _) = loginViaApi(email, password)
            assertTrue(tokenProvider.extractPermissions(accessToken).contains(Permission.PRODUCT_WRITE))

            mockMvc
                .perform(
                    get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            mockMvc
                .perform(
                    get("/api/test-live-auth/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)

            // Revoke PRODUCT_WRITE from role
            val loadedRole = roleRepository.findByName(roleName)!!
            roleRepository.save(loadedRole.revokePermission(Permission.PRODUCT_WRITE))

            // Very next request using same JWT is denied with 403 Forbidden
            mockMvc
                .perform(
                    get("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            mockMvc
                .perform(
                    get("/api/test-live-auth/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))

            val meResult =
                mockMvc
                    .perform(
                        get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                    ).andExpect(status().isOk)
                    .andReturn()
            val permissionsAfter =
                objectMapper
                    .readTree(
                        meResult.response.contentAsString,
                    )["result"]["permissions"]
                    .mapTo(linkedSetOf()) { it.asString() }
            assertEquals(setOf(Permission.USER_READ), permissionsAfter)
        } finally {
            cleanupUser(savedUserId, email)
            tempRoleId?.let { roleId ->
                jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId)
                jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId)
            }
        }
    }

    @Test
    fun `login refresh contract and logout refresh-only revocation remain unchanged`() {
        val email = "contract-logout-${UUID.randomUUID()}@shopflow.org"
        val password = "Password123!"
        var createdUserId: String? = null

        try {
            createdUserId = registerViaApi(email, password)
            val loginResult =
                mockMvc
                    .perform(
                        post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
                    ).andExpect(status().isOk)
                    .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.result.accessToken").isNotEmpty)
                    .andExpect(jsonPath("$.result.refreshToken").isNotEmpty)
                    .andReturn()

            val loginJson = objectMapper.readTree(loginResult.response.contentAsString)
            assertEquals(setOf("code", "message", "status", "result"), loginJson.propertyNames())
            val loginResultNode = loginJson["result"]
            assertEquals(setOf("accessToken", "refreshToken"), loginResultNode.propertyNames())
            val accessToken = loginResultNode["accessToken"].asString()
            val refreshToken = loginResultNode["refreshToken"].asString()

            mockMvc
                .perform(
                    post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                ).andExpect(status().isNoContent)

            // Refresh token is revoked immediately
            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
                ).andExpect(status().isUnauthorized)

            // Policy for this task: logout revokes refresh token only; active user's access token remains usable until expiry
            mockMvc
                .perform(
                    get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
                ).andExpect(status().isOk)
        } finally {
            cleanupUser(createdUserId, email)
        }
    }

    private fun registerViaApi(
        email: String,
        password: String,
    ): String {
        val result =
            mockMvc
                .perform(
                    post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
                ).andExpect(status().isCreated)
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["result"]["id"].asString()
    }

    private fun loginViaApi(
        email: String,
        password: String,
    ): Pair<String, String> {
        val result =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
                ).andExpect(status().isOk)
                .andReturn()
        val json = objectMapper.readTree(result.response.contentAsString)["result"]
        return json["accessToken"].asString() to json["refreshToken"].asString()
    }

    private fun cleanupUser(
        userId: String?,
        email: String,
    ) {
        SecurityContextHolder.clearContext()
        val resolvedId =
            userId ?: jdbcTemplate
                .query(
                    "SELECT id FROM users WHERE email = ?",
                    { rs, _ -> rs.getString("id") },
                    email,
                ).firstOrNull()
        if (resolvedId != null) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", resolvedId)
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", resolvedId)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", resolvedId)
        }
    }

    @RestController
    @RequestMapping("/api/test-live-auth")
    class ProtectedProbeController {
        @GetMapping("/staff-role")
        @PreAuthorize("hasRole('STAFF')")
        fun staffRole(): Map<String, String> = mapOf("status" to "ok")

        @GetMapping("/product-write")
        @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
        fun productWrite(): Map<String, String> = mapOf("status" to "ok")
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
