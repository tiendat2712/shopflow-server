package org.shopflow.backend.infrastructure.security.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.service.TimeProvider
import org.shopflow.backend.domain.service.auth.TokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.Base64
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    @Value("\${jwt.secret}") private val jwtSecret: String,
    private val timeProvider: TimeProvider,
) : TokenProvider {
    private val secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret))
    private val accessTokenValidityMs = 15L * 60L * 1000L
    override val refreshTokenValidityMs: Long = 30L * 24L * 60L * 60L * 1000L

    private fun generateToken(
        userId: UserId,
        type: String,
        expiryMs: Long,
        roles: Set<String> = emptySet(),
        permissions: Set<String> = emptySet(),
    ): String {
        val now = Date.from(timeProvider.now())
        val expiryDate = Date(now.time + expiryMs)
        val builder =
            Jwts
                .builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.value.toString())
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiryDate)

        if (roles.isNotEmpty()) {
            builder.claim("roles", roles.sorted())
        }
        if (permissions.isNotEmpty()) {
            builder.claim("permissions", permissions.sorted())
        }

        return builder
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact()
    }

    override fun generateAccessToken(
        userId: UserId,
        roles: Set<String>,
        permissions: Set<String>,
    ): String =
        generateToken(
            userId = userId,
            type = TOKEN_TYPE_ACCESS,
            expiryMs = accessTokenValidityMs,
            roles = roles,
            permissions = permissions,
        )

    override fun generateRefreshToken(userId: UserId): String = generateToken(userId, TOKEN_TYPE_REFRESH, refreshTokenValidityMs)

    override fun validateAccessToken(token: String): Boolean = hasTokenType(token, TOKEN_TYPE_ACCESS)

    override fun validateRefreshToken(token: String): Boolean = hasTokenType(token, TOKEN_TYPE_REFRESH)

    override fun extractUserId(token: String): UserId {
        val claims = parseAllClaims(token) ?: throw InvalidTokenException("Invalid token.")
        val subject = claims.subject ?: throw InvalidTokenException("Invalid token.")
        return UserId.fromString(subject)
    }

    override fun extractRoles(token: String): Set<String> {
        val roles = extractStringSetClaim(token, "roles")
        if (roles.isNotEmpty()) return roles
        val legacyRole = parseAllClaims(token)?.get("role") as? String
        return if (!legacyRole.isNullOrBlank()) setOf("ROLE_$legacyRole") else emptySet()
    }

    override fun extractPermissions(token: String): Set<String> = extractStringSetClaim(token, "permissions")

    private fun extractStringSetClaim(
        token: String,
        claimName: String,
    ): Set<String> {
        val rawClaim = parseAllClaims(token)?.get(claimName) as? Collection<*> ?: return emptySet()
        return rawClaim.mapNotNullTo(linkedSetOf()) { item ->
            item?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    override fun hashRefreshToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawToken.encodeToByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    private fun hasTokenType(
        token: String,
        expectedType: String,
    ): Boolean {
        val claims = parseAllClaims(token) ?: return false
        if (claims.subject.isNullOrBlank()) return false
        val tokenType = claims["type"] as? String ?: return false
        return tokenType == expectedType
    }

    private fun parseAllClaims(token: String): Claims? {
        val rawToken = token.removePrefix("Bearer ")
        return try {
            Jwts
                .parser()
                .clock { Date.from(timeProvider.now()) }
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(rawToken)
                .payload
        } catch (ex: Exception) {
            null
        }
    }

    private companion object {
        const val TOKEN_TYPE_ACCESS = "access"
        const val TOKEN_TYPE_REFRESH = "refresh"
    }
}
