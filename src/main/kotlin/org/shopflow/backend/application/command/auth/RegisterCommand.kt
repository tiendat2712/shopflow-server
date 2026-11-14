package org.shopflow.backend.application.command.auth

data class RegisterCommand(
    val email: String,
    val rawPassword: String,
    val fullName: String? = null,
    val city: String? = null,
)
