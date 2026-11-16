package org.shopflow.backend.infrastructure.security.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.shopflow.backend.domain.model.auth.Permission
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.service.TimeProvider
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class JwtServiceTest {
    private val testSecretBytes = "test-only-jwt-hmac-secret-key-32bytes!!".encodeToByteArray()
    private val testSecretBase64 = Base64.getEncoder().encodeToString(testSecretBytes)
    private val secretKey = Keys.hmacShaKeyFor(testSecretBytes)

    private lateinit var fixedInstant: Instant
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setUp() {
        fixedInstant = Instant.parse("2026-11-16T10:15:30Z")
        jwtService = JwtService(testSecretBase64, TimeProvider { fixedInstant })
    }

    @Test
    fun `generateRefreshToken produces distinct tokens and hashes with unique jti under fixed clock`() {
        val userId = UserId.generate()

        val token1 = jwtService.generateRefreshToken(userId)
        val token2 = jwtService.generateRefreshToken(userId)

        assertNotEquals(token1, token2)
        assertNotEquals(jwtService.hashRefreshToken(token1), jwtService.hashRefreshToken(token2))

        val claims1 = parseClaims(token1)
        val claims2 = parseClaims(token2)
        val jti1 = claims1.id
        val jti2 = claims2.id
        assertNotNull(jti1)
        assertNotNull(jti2)
        assertNotEquals(jti1, jti2)
        assertEquals(jti1, UUID.fromString(jti1).toString())
        assertEquals(jti2, UUID.fromString(jti2).toString())
        assertEquals(claims1.issuedAt, claims2.issuedAt)
        assertEquals(claims1.expiration, claims2.expiration)

        assertTrue(jwtService.validateRefreshToken(token1))
        assertTrue(jwtService.validateRefreshToken(token2))
        assertFalse(jwtService.validateAccessToken(token1))
        assertFalse(jwtService.validateAccessToken(token2))
        assertEquals(userId, jwtService.extractUserId(token1))
        assertEquals(userId, jwtService.extractUserId(token2))
    }

    @Test
    fun `generateAccessToken produces distinct tokens with unique jti under fixed clock`() {
        val userId = UserId.generate()
        val roles = setOf(Role.ROLE_CUSTOMER)
        val permissions = setOf(Permission.USER_READ, Permission.PRODUCT_READ)

        val token1 = jwtService.generateAccessToken(userId, roles, permissions)
        val token2 = jwtService.generateAccessToken(userId, roles, permissions)

        assertNotEquals(token1, token2)

        val claims1 = parseClaims(token1)
        val claims2 = parseClaims(token2)
        assertNotNull(claims1.id)
        assertNotNull(claims2.id)
        assertNotEquals(claims1.id, claims2.id)
        assertEquals(claims1.id, UUID.fromString(claims1.id).toString())
        assertEquals(claims2.id, UUID.fromString(claims2.id).toString())

        assertTrue(jwtService.validateAccessToken(token1))
        assertTrue(jwtService.validateAccessToken(token2))
        assertFalse(jwtService.validateRefreshToken(token1))
        assertFalse(jwtService.validateRefreshToken(token2))
        assertEquals(userId, jwtService.extractUserId(token1))
        assertEquals(roles, jwtService.extractRoles(token1))
        assertEquals(permissions, jwtService.extractPermissions(token1))
    }

    @Test
    fun `legacy tokens without jti remain valid during transition until expired`() {
        val userId = UserId.generate()
        val now = Date.from(fixedInstant)
        val legacyRefreshToken =
            Jwts
                .builder()
                .subject(userId.value.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(Date(now.time + jwtService.refreshTokenValidityMs))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact()
        val legacyAccessToken =
            Jwts
                .builder()
                .subject(userId.value.toString())
                .claim("type", "access")
                .claim("roles", listOf(Role.ROLE_CUSTOMER))
                .claim("permissions", listOf(Permission.PRODUCT_READ))
                .issuedAt(now)
                .expiration(Date(now.time + 60_000L))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact()

        assertNull(parseClaims(legacyRefreshToken).id)
        assertNull(parseClaims(legacyAccessToken).id)

        assertTrue(jwtService.validateRefreshToken(legacyRefreshToken))
        assertFalse(jwtService.validateAccessToken(legacyRefreshToken))
        assertEquals(userId, jwtService.extractUserId(legacyRefreshToken))

        assertTrue(jwtService.validateAccessToken(legacyAccessToken))
        assertFalse(jwtService.validateRefreshToken(legacyAccessToken))
        assertEquals(userId, jwtService.extractUserId(legacyAccessToken))

        fixedInstant = fixedInstant.plusMillis(60_001L)
        assertFalse(jwtService.validateAccessToken(legacyAccessToken))
        assertTrue(jwtService.validateRefreshToken(legacyRefreshToken))

        fixedInstant = fixedInstant.plusMillis(jwtService.refreshTokenValidityMs)
        assertFalse(jwtService.validateRefreshToken(legacyRefreshToken))
    }

    @Test
    fun `tokens with missing subject wrong signature or non-string jti claim are rejected`() {
        val userId = UserId.generate()
        val now = Date.from(fixedInstant)
        val missingSubjectRefreshToken =
            Jwts
                .builder()
                .id(UUID.randomUUID().toString())
                .subject("   ")
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(Date(now.time + 60_000L))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact()
        val missingSubjectAccessToken =
            Jwts
                .builder()
                .id(UUID.randomUUID().toString())
                .claim("type", "access")
                .issuedAt(now)
                .expiration(Date(now.time + 60_000L))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact()
        val otherSecretKey = Keys.hmacShaKeyFor("another-test-only-jwt-hmac-secret-32b!".encodeToByteArray())
        val wrongSignatureToken =
            Jwts
                .builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.value.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(Date(now.time + 60_000L))
                .signWith(otherSecretKey, Jwts.SIG.HS256)
                .compact()
        val iatSeconds = fixedInstant.epochSecond
        val expSeconds = iatSeconds + 60L
        val nonStringJtiPayload =
            """{"sub":"${userId.value}","type":"refresh","jti":123,"iat":$iatSeconds,"exp":$expSeconds}"""
        val nonStringJtiToken =
            Jwts
                .builder()
                .content(nonStringJtiPayload.encodeToByteArray())
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact()

        assertFalse(jwtService.validateRefreshToken(missingSubjectRefreshToken))
        assertFalse(jwtService.validateAccessToken(missingSubjectAccessToken))
        assertFalse(jwtService.validateRefreshToken(wrongSignatureToken))
        assertFalse(jwtService.validateRefreshToken(nonStringJtiToken))
    }

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .clock { Date.from(fixedInstant) }
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
}
