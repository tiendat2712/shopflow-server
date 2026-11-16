package org.shopflow.backend.infrastructure.security.jwt

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.AuthFailureReason
import org.shopflow.backend.domain.service.auth.UserSecuritySnapshot
import org.shopflow.backend.domain.service.auth.UserSecurityStatePort
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView
import java.time.Instant
import java.util.Base64

class JwtAuthFilterTest {
    private val fixedInstant = Instant.parse("2026-11-16T12:00:00Z")
    private val jwtService =
        JwtService(
            jwtSecret = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".toByteArray()),
            timeProvider = TimeProvider { fixedInstant },
        )

    private val noopResolver =
        HandlerExceptionResolver { _, _, _, _ ->
            null
        }

    @BeforeEach
    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `active user populates authorities strictly from security state port and ignores stale JWT claims`() {
        val tokenUserId = UserId.generate()
        val tokenWithAdminClaims =
            jwtService.generateAccessToken(
                userId = tokenUserId,
                roles = setOf(Role.ROLE_ADMIN, Role.ROLE_STAFF),
                permissions = setOf(Permission.PRODUCT_WRITE, Permission.ORDER_MANAGE_ALL),
            )
        val filter =
            JwtAuthFilter(
                tokenProvider = jwtService,
                userSecurityStatePort =
                    object : UserSecurityStatePort {
                        override fun findByUserId(userId: UserId): UserSecuritySnapshot {
                            assertEquals(tokenUserId, userId)
                            return UserSecuritySnapshot(
                                userId = tokenUserId,
                                status = UserStatus.ACTIVE,
                                roles = setOf(Role.ROLE_CUSTOMER),
                                permissions = setOf(Permission.USER_READ, Permission.PRODUCT_READ),
                            )
                        }
                    },
                handlerExceptionResolver = noopResolver,
            )

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer $tokenWithAdminClaims")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        val authorities = auth?.authorities?.mapTo(linkedSetOf()) { it.authority } ?: emptySet()
        assertEquals(tokenUserId.value.toString(), auth?.principal)
        assertEquals(
            setOf(Role.ROLE_CUSTOMER, Permission.USER_READ, Permission.PRODUCT_READ),
            authorities,
        )
        assertNull(request.getAttribute(AuthFailureReason::class.java.name))
        assertTrue(chain.request != null)
    }

    @Test
    fun `inactive and banned users clear context and mark locked reason attribute without using JWT claims`() {
        val tokenUserId = UserId.generate()
        val token =
            jwtService.generateAccessToken(
                userId = tokenUserId,
                roles = setOf(Role.ROLE_ADMIN),
                permissions = setOf(Permission.PRODUCT_WRITE),
            )

        val expectedReasons =
            mapOf(
                UserStatus.INACTIVE to AuthFailureReason.ACCOUNT_INACTIVE,
                UserStatus.BANNED to AuthFailureReason.ACCOUNT_BANNED,
            )

        expectedReasons.forEach { (lockedStatus, expectedReason) ->
            val filter =
                JwtAuthFilter(
                    tokenProvider = jwtService,
                    userSecurityStatePort =
                        object : UserSecurityStatePort {
                            override fun findByUserId(userId: UserId): UserSecuritySnapshot =
                                UserSecuritySnapshot(
                                    userId = tokenUserId,
                                    status = lockedStatus,
                                    roles = setOf(Role.ROLE_ADMIN),
                                    permissions = setOf(Permission.PRODUCT_WRITE),
                                )
                        },
                    handlerExceptionResolver = noopResolver,
                )

            val request = MockHttpServletRequest()
            request.addHeader("Authorization", "Bearer $token")

            filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

            assertNull(SecurityContextHolder.getContext().authentication)
            assertEquals(expectedReason, request.getAttribute(AuthFailureReason::class.java.name))
        }
    }

    @Test
    fun `deleted user and invalid token mark INVALID_TOKEN attribute and clear security context`() {
        val tokenUserId = UserId.generate()
        val validJwt =
            jwtService.generateAccessToken(
                userId = tokenUserId,
                roles = setOf(Role.ROLE_STAFF),
                permissions = setOf(Permission.PRODUCT_WRITE),
            )
        val filter =
            JwtAuthFilter(
                tokenProvider = jwtService,
                userSecurityStatePort =
                    object : UserSecurityStatePort {
                        override fun findByUserId(userId: UserId): UserSecuritySnapshot? = null
                    },
                handlerExceptionResolver = noopResolver,
            )

        val deletedUserReq = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $validJwt") }
        filter.doFilter(deletedUserReq, MockHttpServletResponse(), MockFilterChain())
        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(AuthFailureReason.INVALID_TOKEN, deletedUserReq.getAttribute(AuthFailureReason::class.java.name))

        val malformedReq = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer invalid.jwt.token") }
        filter.doFilter(malformedReq, MockHttpServletResponse(), MockFilterChain())
        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(AuthFailureReason.INVALID_TOKEN, malformedReq.getAttribute(AuthFailureReason::class.java.name))
    }

    @Test
    fun `security state lookup failure fails closed, delegates to handlerExceptionResolver, and stops filter chain`() {
        val tokenUserId = UserId.generate()
        val token =
            jwtService.generateAccessToken(
                userId = tokenUserId,
                roles = setOf(Role.ROLE_ADMIN),
                permissions = setOf(Permission.PRODUCT_WRITE),
            )
        var capturedException: Exception? = null
        val recordingResolver =
            HandlerExceptionResolver { _: HttpServletRequest, response: HttpServletResponse, _, ex: Exception ->
                capturedException = ex
                response.status = 500
                ModelAndView()
            }
        val filter =
            JwtAuthFilter(
                tokenProvider = jwtService,
                userSecurityStatePort =
                    object : UserSecurityStatePort {
                        override fun findByUserId(userId: UserId): UserSecuritySnapshot? =
                            throw IllegalStateException("Simulated auth state store failure")
                    },
                handlerExceptionResolver = recordingResolver,
            )

        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertTrue(capturedException is IllegalStateException)
        assertEquals(500, response.status)
        assertFalse(chain.request != null, "Filter chain must not continue when auth state resolution fails")
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
