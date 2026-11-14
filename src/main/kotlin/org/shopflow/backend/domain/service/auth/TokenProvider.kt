package org.shopflow.backend.domain.service.auth

import org.shopflow.backend.domain.model.auth.UserId

interface TokenProvider {
    val refreshTokenValidityMs: Long

    fun generateAccessToken(
        userId: UserId,
        roles: Set<String>,
        permissions: Set<String>,
    ): String

    fun generateRefreshToken(userId: UserId): String

    fun validateAccessToken(token: String): Boolean

    fun validateRefreshToken(token: String): Boolean

    fun extractUserId(token: String): UserId

    fun extractRoles(token: String): Set<String>

    fun extractPermissions(token: String): Set<String>

    fun hashRefreshToken(rawToken: String): String
}
