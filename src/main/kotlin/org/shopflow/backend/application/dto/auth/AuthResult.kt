package org.shopflow.backend.application.dto.auth

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
)
