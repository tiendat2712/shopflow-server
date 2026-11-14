package org.shopflow.backend.application.command.auth

data class LoginCommand(
    val email: String,
    val rawPassword: String,
)
