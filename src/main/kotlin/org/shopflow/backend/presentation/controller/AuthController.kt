package org.shopflow.backend.presentation.controller

import jakarta.validation.Valid
import org.shopflow.backend.application.query.auth.GetCurrentUserQuery
import org.shopflow.backend.application.usecase.auth.GetCurrentUserUseCase
import org.shopflow.backend.application.usecase.auth.LoginUseCase
import org.shopflow.backend.application.usecase.auth.LogoutUseCase
import org.shopflow.backend.application.usecase.auth.RefreshTokenUseCase
import org.shopflow.backend.application.usecase.auth.RegisterUseCase
import org.shopflow.backend.application.usecase.auth.UpdateUserProfileUseCase
import org.shopflow.backend.presentation.converter.toCommand
import org.shopflow.backend.presentation.converter.toLogoutCommand
import org.shopflow.backend.presentation.converter.toRefreshCommand
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.ApiCode
import org.shopflow.backend.presentation.dto.ApiResponse
import org.shopflow.backend.presentation.dto.auth.AuthResponse
import org.shopflow.backend.presentation.dto.auth.LoginRequest
import org.shopflow.backend.presentation.dto.auth.RefreshTokenRequest
import org.shopflow.backend.presentation.dto.auth.RegisterRequest
import org.shopflow.backend.presentation.dto.auth.UpdateUserProfileRequest
import org.shopflow.backend.presentation.dto.auth.UserProfileResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val registerUseCase: RegisterUseCase,
    private val loginUseCase: LoginUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val updateUserProfileUseCase: UpdateUserProfileUseCase,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody body: RegisterRequest,
    ): ApiResponse<UserProfileResponse> =
        ApiResponse.created(
            code = ApiCode.AUTH_REGISTER_SUCCESS,
            message = "User registered successfully.",
            result = registerUseCase.execute(body.toCommand()).toResponse(),
        )

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody body: LoginRequest,
    ): ApiResponse<AuthResponse> =
        ApiResponse.ok(
            code = ApiCode.AUTH_LOGIN_SUCCESS,
            message = "Login successful.",
            result = loginUseCase.execute(body.toCommand()).toResponse(),
        )

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody body: RefreshTokenRequest,
    ): ApiResponse<AuthResponse> =
        ApiResponse.ok(
            code = ApiCode.AUTH_REFRESH_SUCCESS,
            message = "Token refreshed successfully.",
            result = refreshTokenUseCase.execute(body.toRefreshCommand()).toResponse(),
        )

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody body: RefreshTokenRequest,
    ) {
        logoutUseCase.execute(body.toLogoutCommand())
    }

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): ApiResponse<UserProfileResponse> =
        ApiResponse.ok(
            code = ApiCode.AUTH_ME_SUCCESS,
            message = "Current user profile retrieved successfully.",
            result =
                getCurrentUserUseCase
                    .execute(GetCurrentUserQuery(authentication.name))
                    .toResponse(),
        )

    @PutMapping("/me/profile")
    fun updateCurrentUserProfile(
        authentication: Authentication,
        @Valid @RequestBody request: UpdateUserProfileRequest,
    ): ApiResponse<UserProfileResponse> =
        ApiResponse.ok(
            code = ApiCode.AUTH_PROFILE_UPDATE_SUCCESS,
            message = "User profile updated successfully.",
            result =
                updateUserProfileUseCase
                    .execute(request.toCommand(authentication.name))
                    .toResponse(),
        )
}
