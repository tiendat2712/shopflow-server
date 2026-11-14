package org.shopflow.backend.application.command.auth

data class RefreshTokenCommand(
    val refreshToken: String,
)
