package org.shopflow.backend.application.dto.auth

import org.shopflow.backend.domain.model.auth.User
import java.time.Instant

data class UserProfileDto(
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

fun User.toProfileDto(): UserProfileDto =
    UserProfileDto(
        id = id.value.toString(),
        email = email.value,
        fullName = fullName,
        phoneNumber = phoneNumber,
        city = city,
        address = address,
        avatarUrl = avatarUrl,
        roles = getRoleNames(),
        permissions = getAllPermissions(),
        status = status.name,
        createdAt = createdAt,
    )
