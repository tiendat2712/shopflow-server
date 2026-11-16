package org.shopflow.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.auth.RefreshTokenCommand
import org.shopflow.backend.application.usecase.auth.RefreshTokenUseCase
import org.shopflow.backend.domain.model.auth.RefreshToken
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.RefreshTokenRepository
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.shopflow.backend.infrastructure.persistence.repository.auth.RefreshTokenRepositoryImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest
@Import(RefreshTokenConcurrencyIntegrationTest.FaultInjectionConfig::class)
class RefreshTokenConcurrencyIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var refreshTokenUseCase: RefreshTokenUseCase

    @Autowired
    private lateinit var faultInjectingRepository: FaultInjectingRefreshTokenRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        faultInjectingRepository.failNextSave.set(false)
        assertEquals("shopflow_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java))
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
    }

    @Test
    fun `concurrent refresh requests with same token allow exactly one 200 OK and reject the other with 401`() {
        val testEmail = "concurrent-refresh-${UUID.randomUUID()}@shopflow.org"
        val rawPassword = "Password123!"
        var registeredUserId: String? = null
        val executor = Executors.newFixedThreadPool(2)

        try {
            registeredUserId = registerUser(testEmail, rawPassword)
            val initialRefreshToken = loginAndGetRefreshToken(testEmail, rawPassword)
            val initialHash = tokenProvider.hashRefreshToken(initialRefreshToken)
            val refreshPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to initialRefreshToken))

            val readyLatch = CountDownLatch(2)
            val startGate = CountDownLatch(1)

            val tasks =
                List(2) {
                    Callable {
                        readyLatch.countDown()
                        assertTrue(startGate.await(5, TimeUnit.SECONDS))
                        performPost("/api/auth/refresh", refreshPayload)
                    }
                }

            val futures = tasks.map { executor.submit(it) }
            assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
            startGate.countDown()

            val results = futures.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 401), results.map { it.status }.sorted())

            val winnerBody = results.single { it.status == 200 }.body
            val rotatedRefreshToken = objectMapper.readTree(winnerBody)["result"]["refreshToken"].asString()
            assertTrue(rotatedRefreshToken.isNotBlank())
            assertNotEquals(initialRefreshToken, rotatedRefreshToken)

            assertEquals(1, countUserRefreshTokens(registeredUserId))
            assertEquals(0, countUserRefreshTokenByHash(registeredUserId, initialHash))
            assertEquals(
                1,
                countUserRefreshTokenByHash(registeredUserId, tokenProvider.hashRefreshToken(rotatedRefreshToken)),
            )

            val rotatedPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to rotatedRefreshToken))
            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rotatedPayload),
                ).andExpect(status().isOk)
        } finally {
            executor.shutdownNow()
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    @Test
    fun `concurrent logout and refresh on same token produce consistent state without 500 error`() {
        val testEmail = "concurrent-logout-refresh-${UUID.randomUUID()}@shopflow.org"
        val rawPassword = "Password123!"
        var registeredUserId: String? = null
        val executor = Executors.newFixedThreadPool(2)

        try {
            registeredUserId = registerUser(testEmail, rawPassword)

            repeat(5) {
                val sessionRefreshToken = loginAndGetRefreshToken(testEmail, rawPassword)
                val sessionHash = tokenProvider.hashRefreshToken(sessionRefreshToken)
                val tokenPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to sessionRefreshToken))

                val readyLatch = CountDownLatch(2)
                val startGate = CountDownLatch(1)

                val logoutFuture =
                    executor.submit(
                        Callable {
                            readyLatch.countDown()
                            assertTrue(startGate.await(5, TimeUnit.SECONDS))
                            performPost("/api/auth/logout", tokenPayload)
                        },
                    )
                val refreshFuture =
                    executor.submit(
                        Callable {
                            readyLatch.countDown()
                            assertTrue(startGate.await(5, TimeUnit.SECONDS))
                            performPost("/api/auth/refresh", tokenPayload)
                        },
                    )

                assertTrue(readyLatch.await(5, TimeUnit.SECONDS))
                startGate.countDown()

                val logoutResult = logoutFuture.get(15, TimeUnit.SECONDS)
                val refreshResult = refreshFuture.get(15, TimeUnit.SECONDS)

                assertEquals(204, logoutResult.status)
                assertEquals("", logoutResult.body)
                assertTrue(refreshResult.status in setOf(200, 401), "Unexpected status: ${refreshResult.status}")

                // Old token must never be usable afterwards
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(tokenPayload),
                    ).andExpect(status().isUnauthorized)
                assertEquals(0, countUserRefreshTokenByHash(registeredUserId, sessionHash))

                if (refreshResult.status == 401) {
                    assertEquals(0, countUserRefreshTokens(registeredUserId))
                } else {
                    val rotatedToken = objectMapper.readTree(refreshResult.body)["result"]["refreshToken"].asString()
                    assertNotEquals(sessionRefreshToken, rotatedToken)
                    assertEquals(1, countUserRefreshTokens(registeredUserId))
                    assertEquals(
                        1,
                        countUserRefreshTokenByHash(registeredUserId, tokenProvider.hashRefreshToken(rotatedToken)),
                    )

                    val rotatedLogoutPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to rotatedToken))
                    mockMvc
                        .perform(
                            post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rotatedLogoutPayload),
                        ).andExpect(status().isNoContent)
                    assertEquals(0, countUserRefreshTokens(registeredUserId))
                }
            }
        } finally {
            executor.shutdownNow()
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    @Test
    fun `failure when saving new refresh token rolls back consumption of old token in same transaction`() {
        val testEmail = "rollback-refresh-${UUID.randomUUID()}@shopflow.org"
        val rawPassword = "Password123!"
        var registeredUserId: String? = null

        try {
            registeredUserId = registerUser(testEmail, rawPassword)
            val initialRefreshToken = loginAndGetRefreshToken(testEmail, rawPassword)
            val initialHash = tokenProvider.hashRefreshToken(initialRefreshToken)

            faultInjectingRepository.failNextSave.set(true)
            assertThrows(IllegalStateException::class.java) {
                refreshTokenUseCase.execute(RefreshTokenCommand(initialRefreshToken))
            }

            // Old token consumption must have rolled back when save failed
            assertEquals(1, countUserRefreshTokens(registeredUserId))
            assertEquals(1, countUserRefreshTokenByHash(registeredUserId, initialHash))

            val refreshPayload = objectMapper.writeValueAsString(mapOf("refreshToken" to initialRefreshToken))
            val retryResult =
                mockMvc
                    .perform(
                        post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshPayload),
                    ).andExpect(status().isOk)
                    .andReturn()

            val rotatedToken = objectMapper.readTree(retryResult.response.contentAsString)["result"]["refreshToken"].asString()
            assertTrue(rotatedToken.isNotBlank())
            assertNotEquals(initialRefreshToken, rotatedToken)
            assertEquals(1, countUserRefreshTokens(registeredUserId))
            assertEquals(0, countUserRefreshTokenByHash(registeredUserId, initialHash))
            assertEquals(
                1,
                countUserRefreshTokenByHash(registeredUserId, tokenProvider.hashRefreshToken(rotatedToken)),
            )
        } finally {
            faultInjectingRepository.failNextSave.set(false)
            cleanupTestUser(registeredUserId, testEmail)
        }
    }

    private fun performPost(
        path: String,
        payload: String,
    ): HttpCallResult =
        try {
            val mvcResult =
                mockMvc
                    .perform(
                        post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload),
                    ).andReturn()
            HttpCallResult(
                status = mvcResult.response.status,
                body = mvcResult.response.contentAsString,
            )
        } finally {
            SecurityContextHolder.clearContext()
        }

    private fun registerUser(
        email: String,
        password: String,
    ): String {
        val payload = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val result =
            mockMvc
                .perform(
                    post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload),
                ).andExpect(status().isCreated)
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["result"]["id"].asString()
    }

    private fun loginAndGetRefreshToken(
        email: String,
        password: String,
    ): String {
        val payload = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val result =
            mockMvc
                .perform(
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload),
                ).andExpect(status().isOk)
                .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["result"]["refreshToken"].asString()
    }

    private fun countUserRefreshTokens(userId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
            Int::class.java,
            userId,
        ) ?: 0

    private fun countUserRefreshTokenByHash(
        userId: String,
        hashedToken: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND hashed_token = ?",
            Int::class.java,
            userId,
            hashedToken,
        ) ?: 0

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

    private data class HttpCallResult(
        val status: Int,
        val body: String,
    )

    class FaultInjectingRefreshTokenRepository(
        private val delegate: RefreshTokenRepositoryImpl,
    ) : RefreshTokenRepository {
        val failNextSave = AtomicBoolean(false)

        override fun findByUserIdAndHashedToken(
            userId: UserId,
            hashedToken: String,
        ): RefreshToken? = delegate.findByUserIdAndHashedToken(userId, hashedToken)

        override fun consumeIfValid(
            userId: UserId,
            hashedToken: String,
            now: Instant,
        ): Boolean = delegate.consumeIfValid(userId, hashedToken, now)

        override fun deleteByUserIdAndHashedToken(
            userId: UserId,
            hashedToken: String,
        ): Boolean = delegate.deleteByUserIdAndHashedToken(userId, hashedToken)

        override fun deleteAllByUserId(userId: UserId) {
            delegate.deleteAllByUserId(userId)
        }

        override fun save(refreshToken: RefreshToken): RefreshToken {
            if (failNextSave.compareAndSet(true, false)) {
                throw IllegalStateException("Simulated save failure")
            }
            return delegate.save(refreshToken)
        }
    }

    @TestConfiguration
    class FaultInjectionConfig {
        @Bean
        @Primary
        fun faultInjectingRefreshTokenRepository(delegate: RefreshTokenRepositoryImpl): FaultInjectingRefreshTokenRepository =
            FaultInjectingRefreshTokenRepository(delegate)
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
