package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.auth.LoginCommand
import org.shopflow.backend.application.command.auth.RegisterCommand
import org.shopflow.backend.application.query.auth.GetCurrentUserQuery
import org.shopflow.backend.application.usecase.auth.GetCurrentUserUseCase
import org.shopflow.backend.application.usecase.auth.LoginUseCase
import org.shopflow.backend.application.usecase.auth.RegisterUseCase
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.repository.auth.RoleRepository
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.infrastructure.config.JpaAuditingConfig
import org.shopflow.backend.infrastructure.persistence.jpa.auth.PermissionJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RefreshTokenJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RoleJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.UserJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(RbacAndAuditingIntegrationTest.TestRbacController::class)
class RbacAndAuditingIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var permissionJpaRepository: PermissionJpaRepository

    @Autowired
    private lateinit var roleJpaRepository: RoleJpaRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var refreshTokenJpaRepository: RefreshTokenJpaRepository

    @Autowired
    private lateinit var registerUseCase: RegisterUseCase

    @Autowired
    private lateinit var loginUseCase: LoginUseCase

    @Autowired
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase

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
    fun `seeded permissions and roles exist with BaseJpaEntity audit metadata and no legacy user role column`() {
        val legacyColumnCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'users'
                  AND column_name = 'role'
                """.trimIndent(),
                Int::class.java,
            ) ?: -1
        assertEquals(0, legacyColumnCount)

        val permissions = permissionJpaRepository.findAll()
        val expectedCodes =
            setOf(
                Permission.USER_READ,
                Permission.USER_MANAGE,
                Permission.PRODUCT_READ,
                Permission.PRODUCT_WRITE,
                Permission.ORDER_CREATE,
                Permission.ORDER_READ_OWN,
                Permission.ORDER_MANAGE_ALL,
                Permission.PAYMENT_VERIFY,
                Permission.FLASH_SALE_MANAGE,
            )
        assertTrue(permissions.map { it.code }.containsAll(expectedCodes))
        permissions.forEach { entity ->
            assertNotNull(entity.createdAt)
            assertNotNull(entity.updatedAt)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, entity.createdBy)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, entity.updatedBy)
        }

        val roleEntities = roleJpaRepository.findAll().associateBy { it.name }
        assertEquals(3, roleEntities.size)
        roleEntities.values.forEach { roleEntity ->
            assertNotNull(roleEntity.createdAt)
            assertNotNull(roleEntity.updatedAt)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, roleEntity.createdBy)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, roleEntity.updatedBy)
        }

        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        assertEquals(
            setOf(
                Permission.USER_READ,
                Permission.PRODUCT_READ,
                Permission.ORDER_CREATE,
                Permission.ORDER_READ_OWN,
            ),
            customerRole.getPermissionCodes(),
        )

        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        assertEquals(
            setOf(
                Permission.USER_READ,
                Permission.PRODUCT_READ,
                Permission.PRODUCT_WRITE,
                Permission.ORDER_READ_OWN,
                Permission.ORDER_MANAGE_ALL,
                Permission.PAYMENT_VERIFY,
            ),
            staffRole.getPermissionCodes(),
        )

        val adminRole = roleRepository.findByName(Role.ROLE_ADMIN)!!
        assertEquals(expectedCodes, adminRole.getPermissionCodes())
    }

    @Test
    fun `register login query and update preserve RBAC authorities and auditing fields`() {
        val uniqueEmail = "rbac-test-${UUID.randomUUID()}@shopflow.org"
        var createdUserId: String? = null

        try {
            val registered = registerUseCase.execute(RegisterCommand(uniqueEmail, "Password123!"))
            createdUserId = registered.id

            assertEquals(setOf(Role.ROLE_CUSTOMER), registered.roles)
            assertEquals(
                setOf(
                    Permission.USER_READ,
                    Permission.PRODUCT_READ,
                    Permission.ORDER_CREATE,
                    Permission.ORDER_READ_OWN,
                ),
                registered.permissions,
            )

            val savedUserEntity = userJpaRepository.findById(createdUserId).orElseThrow()
            assertNotNull(savedUserEntity.createdAt)
            assertNotNull(savedUserEntity.updatedAt)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, savedUserEntity.createdBy)
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, savedUserEntity.updatedBy)

            val loginResult = loginUseCase.execute(LoginCommand(uniqueEmail, "Password123!"))
            assertEquals(setOf(Role.ROLE_CUSTOMER), tokenProvider.extractRoles(loginResult.accessToken))
            assertEquals(registered.permissions, tokenProvider.extractPermissions(loginResult.accessToken))

            val queriedProfile = getCurrentUserUseCase.execute(GetCurrentUserQuery(createdUserId))
            assertEquals(registered.roles, queriedProfile.roles)
            assertEquals(registered.permissions, queriedProfile.permissions)

            val refreshCount =
                jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                    Int::class.java,
                    createdUserId,
                ) ?: 0
            assertEquals(1, refreshCount)

            val actorId = UUID.randomUUID().toString()
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(actorId, null, emptyList())

            val domainUser = userRepository.findById(UserId.fromString(createdUserId))!!
            userRepository.save(domainUser.copy(status = UserStatus.INACTIVE))

            val updatedUserEntity = userJpaRepository.findById(createdUserId).orElseThrow()
            assertEquals(JpaAuditingConfig.SYSTEM_AUDITOR, updatedUserEntity.createdBy)
            assertEquals(actorId, updatedUserEntity.updatedBy)
        } finally {
            SecurityContextHolder.clearContext()
            if (createdUserId != null) {
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", createdUserId)
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", createdUserId)
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId)
            }
        }
    }

    @Test
    fun `method security enforces PreAuthorize hasAuthority and hasRole via JwtAuthFilter`() {
        val customerRole = roleRepository.findByName(Role.ROLE_CUSTOMER)!!
        val staffRole = roleRepository.findByName(Role.ROLE_STAFF)!!
        val adminRole = roleRepository.findByName(Role.ROLE_ADMIN)!!

        val now = Instant.now()
        val createdUserIds = mutableListOf<String>()

        fun saveActor(
            label: String,
            role: Role,
        ): User {
            val id = UserId.generate()
            val saved =
                userRepository.save(
                    User(
                        id = id,
                        email = Email.of("rbac-$label-${id.value}@shopflow.org"),
                        hashedPassword = HashedPassword("rbac-unused-password-hash"),
                        roles = setOf(role),
                        status = UserStatus.ACTIVE,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            createdUserIds += saved.id.value.toString()
            return saved
        }

        try {
            val customerUser = saveActor("customer", customerRole)
            val staffUser = saveActor("staff", staffRole)
            val adminUser = saveActor("admin", adminRole)

            val customerToken =
                tokenProvider.generateAccessToken(
                    userId = customerUser.id,
                    roles = setOf(customerRole.name),
                    permissions = customerRole.getPermissionCodes(),
                )
            val staffToken =
                tokenProvider.generateAccessToken(
                    userId = staffUser.id,
                    roles = setOf(staffRole.name),
                    permissions = staffRole.getPermissionCodes(),
                )
            val adminToken =
                tokenProvider.generateAccessToken(
                    userId = adminUser.id,
                    roles = setOf(adminRole.name),
                    permissions = adminRole.getPermissionCodes(),
                )

            mockMvc
                .perform(get("/api/test-rbac/product-write"))
                .andExpect(status().isUnauthorized)

            mockMvc
                .perform(
                    get("/api/test-rbac/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $customerToken"),
                ).andExpect(status().isForbidden)

            mockMvc
                .perform(
                    get("/api/test-rbac/product-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                ).andExpect(status().isOk)

            mockMvc
                .perform(
                    get("/api/test-rbac/admin-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $staffToken"),
                ).andExpect(status().isForbidden)

            mockMvc
                .perform(
                    get("/api/test-rbac/admin-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
                ).andExpect(status().isOk)
        } finally {
            SecurityContextHolder.clearContext()
            createdUserIds.forEach { userId ->
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId)
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId)
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
            }
        }
    }

    @RestController
    @RequestMapping("/api/test-rbac")
    class TestRbacController {
        @GetMapping("/product-write")
        @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
        fun productWrite(): Map<String, String> = mapOf("status" to "ok")

        @GetMapping("/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        fun adminOnly(): Map<String, String> = mapOf("status" to "ok")
    }
}
