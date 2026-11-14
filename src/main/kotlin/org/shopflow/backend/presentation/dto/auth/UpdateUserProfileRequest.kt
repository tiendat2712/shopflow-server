package org.shopflow.backend.presentation.dto.auth

import jakarta.validation.constraints.Size

data class UpdateUserProfileRequest(
    @field:Size(max = 150, message = "Full name must not exceed 150 characters.")
    val fullName: String? = null,
    @field:Size(max = 30, message = "Phone number must not exceed 30 characters.")
    val phoneNumber: String? = null,
    @field:Size(max = 100, message = "City must not exceed 100 characters.")
    val city: String? = null,
    @field:Size(max = 500, message = "Address must not exceed 500 characters.")
    val address: String? = null,
    @field:Size(max = 500, message = "Avatar URL must not exceed 500 characters.")
    val avatarUrl: String? = null,
)
