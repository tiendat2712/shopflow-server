package org.shopflow.backend.presentation.dto.auth

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required.")
    val refreshToken: String,
)
