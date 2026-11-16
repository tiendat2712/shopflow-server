package org.shopflow.backend.presentation.exceptionhandler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.shopflow.backend.domain.service.auth.AuthFailureReason
import org.shopflow.backend.presentation.dto.ApiCode
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class ApiAuthenticationEntryPoint(
    private val errorWriter: ApiErrorResponseWriter,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        when (request.getAttribute(AuthFailureReason::class.java.name) as? AuthFailureReason) {
            AuthFailureReason.ACCOUNT_INACTIVE ->
                errorWriter.write(
                    request = request,
                    response = response,
                    status = HttpStatus.FORBIDDEN,
                    code = ApiCode.ACCOUNT_INACTIVE,
                    message = "User account is inactive.",
                )
            AuthFailureReason.ACCOUNT_BANNED ->
                errorWriter.write(
                    request = request,
                    response = response,
                    status = HttpStatus.FORBIDDEN,
                    code = ApiCode.ACCOUNT_INACTIVE,
                    message = "User account is banned.",
                )
            AuthFailureReason.INVALID_TOKEN ->
                errorWriter.write(
                    request = request,
                    response = response,
                    status = HttpStatus.UNAUTHORIZED,
                    code = ApiCode.INVALID_TOKEN,
                    message = "Invalid or expired access token.",
                )
            null ->
                errorWriter.write(
                    request = request,
                    response = response,
                    status = HttpStatus.UNAUTHORIZED,
                    code = ApiCode.UNAUTHORIZED,
                    message = "Authentication is required to access this resource.",
                )
        }
    }
}

@Component
class ApiAccessDeniedHandler(
    private val errorWriter: ApiErrorResponseWriter,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        errorWriter.write(
            request = request,
            response = response,
            status = HttpStatus.FORBIDDEN,
            code = ApiCode.ACCESS_DENIED,
            message = "You do not have permission to perform this action.",
        )
    }
}
