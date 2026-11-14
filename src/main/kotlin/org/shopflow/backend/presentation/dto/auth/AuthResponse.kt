package org.shopflow.backend.presentation.dto.auth

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
)
