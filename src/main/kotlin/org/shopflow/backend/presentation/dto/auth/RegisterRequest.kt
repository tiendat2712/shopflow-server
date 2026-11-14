package org.shopflow.backend.presentation.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.shopflow.backend.presentation.validation.ValidPassword

data class RegisterRequest(
    @field:NotBlank(message = "Email is required.")
    @field:Email(message = "Invalid email format.")
    val email: String,
    @field:ValidPassword
    val password: String,
    @field:Size(max = 150, message = "Full name must not exceed 150 characters.")
    val fullName: String? = null,
    @field:Size(max = 100, message = "City must not exceed 100 characters.")
    val city: String? = null,
)
