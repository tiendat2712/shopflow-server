package org.shopflow.backend.application.command.auth

data class LogoutCommand(
    val refreshToken: String,
)
