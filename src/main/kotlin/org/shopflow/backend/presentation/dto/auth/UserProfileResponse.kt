package org.shopflow.backend.presentation.dto.auth

import java.time.Instant

data class UserProfileResponse(
    val id: String,
    val email: String,
    val fullName: String?,
    val phoneNumber: String?,
    val city: String?,
    val address: String?,
    val avatarUrl: String?,
    val roles: Set<String>,
    val permissions: Set<String>,
    val status: String,
    val createdAt: Instant,
)
