package org.shopflow.backend.presentation.converter

import org.shopflow.backend.application.command.auth.LoginCommand
import org.shopflow.backend.application.command.auth.LogoutCommand
import org.shopflow.backend.application.command.auth.RefreshTokenCommand
import org.shopflow.backend.application.command.auth.RegisterCommand
import org.shopflow.backend.application.command.auth.UpdateUserProfileCommand
import org.shopflow.backend.application.dto.auth.AuthResult
import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.presentation.dto.auth.AuthResponse
import org.shopflow.backend.presentation.dto.auth.LoginRequest
import org.shopflow.backend.presentation.dto.auth.RefreshTokenRequest
import org.shopflow.backend.presentation.dto.auth.RegisterRequest
import org.shopflow.backend.presentation.dto.auth.UpdateUserProfileRequest
import org.shopflow.backend.presentation.dto.auth.UserProfileResponse

fun RegisterRequest.toCommand(): RegisterCommand =
    RegisterCommand(
        email = email,
        rawPassword = password,
        fullName = fullName,
        city = city,
    )

fun UpdateUserProfileRequest.toCommand(userId: String): UpdateUserProfileCommand =
    UpdateUserProfileCommand(
        userId = userId,
        fullName = fullName,
        phoneNumber = phoneNumber,
        city = city,
        address = address,
        avatarUrl = avatarUrl,
    )

fun LoginRequest.toCommand(): LoginCommand =
    LoginCommand(
        email = email,
        rawPassword = password,
    )

fun RefreshTokenRequest.toRefreshCommand(): RefreshTokenCommand = RefreshTokenCommand(refreshToken = refreshToken)

fun RefreshTokenRequest.toLogoutCommand(): LogoutCommand = LogoutCommand(refreshToken = refreshToken)

fun AuthResult.toResponse(): AuthResponse =
    AuthResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

fun UserProfileDto.toResponse(): UserProfileResponse =
    UserProfileResponse(
        id = id,
        email = email,
        fullName = fullName,
        phoneNumber = phoneNumber,
        city = city,
        address = address,
        avatarUrl = avatarUrl,
        roles = roles,
        permissions = permissions,
        status = status,
        createdAt = createdAt,
    )
