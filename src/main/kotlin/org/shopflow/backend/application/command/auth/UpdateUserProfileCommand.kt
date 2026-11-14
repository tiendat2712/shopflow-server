package org.shopflow.backend.application.command.auth

data class UpdateUserProfileCommand(
    val userId: String,
    val fullName: String?,
    val phoneNumber: String?,
    val city: String?,
    val address: String?,
    val avatarUrl: String?,
)
