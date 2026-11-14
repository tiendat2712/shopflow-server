package org.shopflow.backend.presentation.exceptionhandler

import org.shopflow.backend.application.exception.ApplicationException
import org.shopflow.backend.domain.exception.AccountInactiveException
import org.shopflow.backend.domain.exception.CategoryNotFoundException
import org.shopflow.backend.domain.exception.DatasetImportException
import org.shopflow.backend.domain.exception.DomainException
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.domain.exception.DuplicateCategorySlugException
import org.shopflow.backend.domain.exception.DuplicateProductSkuException
import org.shopflow.backend.domain.exception.DuplicateProductSlugException
import org.shopflow.backend.domain.exception.ImageStorageException
import org.shopflow.backend.domain.exception.InvalidCredentialsException
import org.shopflow.backend.domain.exception.InvalidMediaFileException
import org.shopflow.backend.domain.exception.InvalidTokenException
import org.shopflow.backend.domain.exception.PermissionNotFoundException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException
import org.shopflow.backend.domain.exception.UserNotFoundException
import org.shopflow.backend.presentation.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors =
            ex.bindingResult.allErrors.map {
                it.defaultMessage ?: "Invalid value"
            }
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            message = errors.firstOrNull() ?: "Validation failed.",
            errors = errors,
        )
    }

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ErrorResponse> {
        val status =
            when (ex) {
                is DomainValidationException,
                is InvalidMediaFileException,
                is DatasetImportException,
                -> HttpStatus.BAD_REQUEST
                is InvalidCredentialsException, is InvalidTokenException -> HttpStatus.UNAUTHORIZED
                is AccountInactiveException -> HttpStatus.FORBIDDEN
                is UserNotFoundException,
                is RoleNotFoundException,
                is PermissionNotFoundException,
                is CategoryNotFoundException,
                is ProductNotFoundException,
                -> HttpStatus.NOT_FOUND
                is UserAlreadyExistsException,
                is DuplicateCategorySlugException,
                is DuplicateProductSkuException,
                is DuplicateProductSlugException,
                -> HttpStatus.CONFLICT
                is ImageStorageException -> HttpStatus.BAD_GATEWAY
            }
        return buildErrorResponse(status = status, message = ex.message)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        @Suppress("UNUSED_PARAMETER") ex: MaxUploadSizeExceededException,
    ): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.valueOf(413),
            message = "Uploaded file exceeds maximum allowed size of 5 MB.",
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) {
            throw ex
        }
        return buildErrorResponse(
            status = HttpStatus.FORBIDDEN,
            message = "You do not have permission to perform this action.",
        )
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(ex: ApplicationException): ResponseEntity<ErrorResponse> =
        buildErrorResponse(status = HttpStatus.BAD_REQUEST, message = ex.message)

    private fun buildErrorResponse(
        status: HttpStatus,
        message: String,
        errors: List<String> = listOf(message),
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(
                ErrorResponse(
                    status = status.value(),
                    error = status.reasonPhrase,
                    message = message,
                    errors = errors,
                ),
            )
}
