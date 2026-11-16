package org.shopflow.backend.presentation.exceptionhandler

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
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
import org.shopflow.backend.domain.exception.ProductConcurrencyConflictException
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.exception.UserAlreadyExistsException
import org.shopflow.backend.domain.exception.UserNotFoundException
import org.shopflow.backend.presentation.dto.ApiCode
import org.shopflow.backend.presentation.dto.ApiErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JsonToken
import tools.jackson.databind.exc.InvalidNullException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.json.JsonMapper

@RestControllerAdvice
class GlobalExceptionHandler
    @Autowired
    constructor(
        private val errorWriter: ApiErrorResponseWriter,
    ) {
        constructor() : this(
            ApiErrorResponseWriter(JsonMapper.builder().findAndAddModules().build()),
        )

        private val logger = LoggerFactory.getLogger(javaClass)

        @ExceptionHandler(BindException::class)
        fun handleValidationError(
            ex: BindException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val details = mutableListOf<ApiErrorDetail>()
            ex.bindingResult.fieldErrors.forEach { fieldError ->
                details +=
                    ApiErrorDetail(
                        field = fieldError.field,
                        code = mapFieldErrorCode(fieldError),
                        message = resolveFieldErrorMessage(fieldError),
                    )
            }
            ex.bindingResult.globalErrors.forEach { globalError ->
                details +=
                    ApiErrorDetail(
                        field = null,
                        code = mapCodes(globalError.codes) ?: ApiCode.INVALID_OBJECT,
                        message = globalError.defaultMessage ?: "Invalid request data.",
                    )
            }
            val normalizedDetails =
                details.ifEmpty {
                    listOf(ApiErrorDetail(field = null, code = ApiCode.VALIDATION_ERROR, message = "Validation failed."))
                }
            return errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.VALIDATION_ERROR,
                message = "Invalid request data.",
                errors = normalizedDetails,
                request = request,
            )
        }

        @ExceptionHandler(HandlerMethodValidationException::class)
        fun handleHandlerMethodValidation(
            ex: HandlerMethodValidationException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val details = mutableListOf<ApiErrorDetail>()
            ex.parameterValidationResults.forEach { paramResult ->
                val paramName = paramResult.methodParameter.parameterName
                paramResult.resolvableErrors.forEach { resolvable ->
                    val detailCode = mapCodes(resolvable.codes) ?: ApiCode.INVALID_VALUE
                    val message = resolvable.defaultMessage ?: "Invalid value."
                    details +=
                        ApiErrorDetail(
                            field = paramName,
                            code = detailCode,
                            message = message,
                        )
                }
            }
            if (details.isEmpty()) {
                ex.allErrors.forEach { err ->
                    details +=
                        ApiErrorDetail(
                            field = null,
                            code = mapCodes(err.codes) ?: ApiCode.INVALID_VALUE,
                            message = err.defaultMessage ?: "Invalid value.",
                        )
                }
            }
            val normalizedDetails =
                details.ifEmpty {
                    listOf(ApiErrorDetail(field = null, code = ApiCode.VALIDATION_ERROR, message = "Validation failed."))
                }
            return errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.VALIDATION_ERROR,
                message = "Invalid request data.",
                errors = normalizedDetails,
                request = request,
            )
        }

        @ExceptionHandler(ConstraintViolationException::class)
        fun handleConstraintViolation(
            ex: ConstraintViolationException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val details =
                ex.constraintViolations
                    .map { violation ->
                        ApiErrorDetail(
                            field = extractViolationField(violation),
                            code =
                                mapConstraintSimpleName(
                                    violation.constraintDescriptor.annotation.annotationClass.simpleName,
                                ),
                            message = violation.message ?: "Invalid value.",
                        )
                    }.ifEmpty {
                        listOf(ApiErrorDetail(field = null, code = ApiCode.VALIDATION_ERROR, message = "Validation failed."))
                    }
            return errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.VALIDATION_ERROR,
                message = "Invalid request data.",
                errors = details,
                request = request,
            )
        }

        @ExceptionHandler(HttpMessageNotReadableException::class)
        fun handleHttpMessageNotReadable(
            ex: HttpMessageNotReadableException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            if (ex.cause == null && ex.message?.contains("Required request body is missing", ignoreCase = true) == true) {
                return errorWriter.toResponseEntity(
                    status = HttpStatus.BAD_REQUEST,
                    code = ApiCode.MISSING_REQUEST_BODY,
                    message = "Request body is required.",
                    request = request,
                )
            }

            when (val cause = ex.cause) {
                is MismatchedInputException -> {
                    val fieldPath =
                        cause.path
                            .mapNotNull { ref ->
                                ref.propertyName ?: ref.index.takeIf { it >= 0 }?.let { "[$it]" }
                            }.joinToString(".")
                    if (fieldPath.isNotEmpty()) {
                        val (detailCode, message) =
                            when {
                                cause is InvalidNullException || cause.currentToken == JsonToken.VALUE_NULL ->
                                    ApiCode.REQUIRED_FIELD to "Field '$fieldPath' cannot be null."
                                cause.originalMessage?.contains("missing", ignoreCase = true) == true ->
                                    ApiCode.REQUIRED_FIELD to "Field '$fieldPath' is required."
                                else ->
                                    ApiCode.TYPE_MISMATCH to "Invalid value for field '$fieldPath'."
                            }
                        return errorWriter.toResponseEntity(
                            status = HttpStatus.BAD_REQUEST,
                            code = ApiCode.VALIDATION_ERROR,
                            message = "Invalid request data.",
                            errors =
                                listOf(
                                    ApiErrorDetail(
                                        field = fieldPath,
                                        code = detailCode,
                                        message = message,
                                    ),
                                ),
                            request = request,
                        )
                    }
                    return errorWriter.toResponseEntity(
                        status = HttpStatus.BAD_REQUEST,
                        code = ApiCode.MALFORMED_JSON,
                        message = "Malformed or invalid JSON request body.",
                        request = request,
                    )
                }
                else ->
                    return errorWriter.toResponseEntity(
                        status = HttpStatus.BAD_REQUEST,
                        code = ApiCode.MALFORMED_JSON,
                        message = "Malformed JSON request body.",
                        request = request,
                    )
            }
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException::class)
        fun handleMethodArgumentTypeMismatch(
            ex: MethodArgumentTypeMismatchException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val detailMessage = "Invalid value for parameter '${ex.name}'."
            return errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.INVALID_PARAMETER_TYPE,
                message = "Invalid request parameter.",
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = ex.name,
                            code = ApiCode.TYPE_MISMATCH,
                            message = detailMessage,
                        ),
                    ),
                request = request,
            )
        }

        @ExceptionHandler(MissingServletRequestParameterException::class)
        fun handleMissingRequestParameter(
            ex: MissingServletRequestParameterException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.MISSING_PARAMETER,
                message = "Required request parameter is missing.",
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = ex.parameterName,
                            code = ApiCode.MISSING_PARAMETER,
                            message = "Required parameter '${ex.parameterName}' is missing.",
                        ),
                    ),
                request = request,
            )

        @ExceptionHandler(MissingPathVariableException::class)
        fun handleMissingPathVariable(
            ex: MissingPathVariableException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.MISSING_PARAMETER,
                message = "Required path variable is missing.",
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = ex.variableName,
                            code = ApiCode.MISSING_PARAMETER,
                            message = "Required path variable '${ex.variableName}' is missing.",
                        ),
                    ),
                request = request,
            )

        @ExceptionHandler(MissingServletRequestPartException::class)
        fun handleMissingServletRequestPart(
            ex: MissingServletRequestPartException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.MISSING_MULTIPART_PART,
                message = "Required multipart part is missing.",
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = ex.requestPartName,
                            code = ApiCode.MISSING_MULTIPART_PART,
                            message = "Required multipart part '${ex.requestPartName}' is missing.",
                        ),
                    ),
                request = request,
            )

        @ExceptionHandler(ServletRequestBindingException::class)
        fun handleServletRequestBinding(
            ex: ServletRequestBindingException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.VALIDATION_ERROR,
                message = "Invalid request parameters or headers.",
                request = request,
            )

        @ExceptionHandler(MaxUploadSizeExceededException::class)
        fun handleMaxUploadSizeExceeded(
            @Suppress("UNUSED_PARAMETER") ex: MaxUploadSizeExceededException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val message = "Uploaded file exceeds maximum allowed size of 5 MB."
            return errorWriter.toResponseEntity(
                status = HttpStatus.valueOf(413),
                code = ApiCode.PAYLOAD_TOO_LARGE,
                message = message,
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = "file",
                            code = ApiCode.FILE_TOO_LARGE,
                            message = message,
                        ),
                    ),
                request = request,
            )
        }

        @ExceptionHandler(MultipartException::class)
        fun handleMultipartException(
            @Suppress("UNUSED_PARAMETER") ex: MultipartException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.VALIDATION_ERROR,
                message = "Invalid multipart request.",
                request = request,
            )

        @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
        fun handleMethodNotSupported(
            ex: HttpRequestMethodNotSupportedException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val headers = HttpHeaders()
            ex.supportedHttpMethods?.takeIf { it.isNotEmpty() }?.let { supported ->
                headers.allow = supported
            }
            return errorWriter.toResponseEntity(
                status = HttpStatus.METHOD_NOT_ALLOWED,
                code = ApiCode.METHOD_NOT_ALLOWED,
                message = "Request method '${ex.method}' is not supported.",
                headers = headers,
                request = request,
            )
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
        fun handleMediaTypeNotSupported(
            ex: HttpMediaTypeNotSupportedException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val headers = HttpHeaders()
            if (ex.supportedMediaTypes.isNotEmpty()) {
                headers.accept = ex.supportedMediaTypes
            }
            val contentTypeLabel = ex.contentType?.toString() ?: "unknown"
            return errorWriter.toResponseEntity(
                status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                code = ApiCode.UNSUPPORTED_MEDIA_TYPE,
                message = "Content-Type '$contentTypeLabel' is not supported.",
                headers = headers,
                request = request,
            )
        }

        @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
        fun handleMediaTypeNotAcceptable(
            @Suppress("UNUSED_PARAMETER") ex: HttpMediaTypeNotAcceptableException,
        ): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()

        @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
        fun handleNotFound(
            @Suppress("UNUSED_PARAMETER") ex: Exception,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.NOT_FOUND,
                code = ApiCode.ENDPOINT_NOT_FOUND,
                message = "The requested endpoint was not found.",
                request = request,
            )

        @ExceptionHandler(DomainException::class)
        fun handleDomainException(
            ex: DomainException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val mapping = mapDomainException(ex)
            return errorWriter.toResponseEntity(
                status = mapping.status,
                code = mapping.code,
                message = ex.message,
                errors =
                    listOf(
                        ApiErrorDetail(
                            field = mapping.field,
                            code = mapping.detailCode,
                            message = ex.message,
                        ),
                    ),
                request = request,
            )
        }

        @ExceptionHandler(AccessDeniedException::class)
        fun handleAccessDeniedException(
            ex: AccessDeniedException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            val auth = SecurityContextHolder.getContext().authentication
            if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) {
                throw ex
            }
            return errorWriter.toResponseEntity(
                status = HttpStatus.FORBIDDEN,
                code = ApiCode.ACCESS_DENIED,
                message = "You do not have permission to perform this action.",
                request = request,
            )
        }

        @ExceptionHandler(ApplicationException::class)
        fun handleApplicationException(
            ex: ApplicationException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> =
            errorWriter.toResponseEntity(
                status = HttpStatus.BAD_REQUEST,
                code = ApiCode.APPLICATION_ERROR,
                message = ex.message,
                request = request,
            )

        @ExceptionHandler(DataIntegrityViolationException::class)
        fun handleDataIntegrityViolation(
            ex: DataIntegrityViolationException,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            logger.error(
                "Unhandled data integrity violation on {} {}",
                request?.method ?: "UNKNOWN",
                request?.requestURI ?: "UNKNOWN",
                ex,
            )
            return errorWriter.toResponseEntity(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ApiCode.INTERNAL_SERVER_ERROR,
                message = "An unexpected error occurred.",
                request = request,
            )
        }

        @ExceptionHandler(Exception::class)
        fun handleUnexpectedException(
            ex: Exception,
            request: HttpServletRequest? = null,
        ): ResponseEntity<Any> {
            logger.error(
                "Unhandled exception on {} {}: {}",
                request?.method ?: "UNKNOWN",
                request?.requestURI ?: "UNKNOWN",
                ex.javaClass.simpleName,
                ex,
            )
            return errorWriter.toResponseEntity(
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                code = ApiCode.INTERNAL_SERVER_ERROR,
                message = "An unexpected error occurred.",
                request = request,
            )
        }

        private fun mapDomainException(ex: DomainException): DomainErrorMapping =
            when (ex) {
                is DomainValidationException -> {
                    val field = inferValidationField(ex.message)
                    val detailCode =
                        if (field == "price" ||
                            ex.message.contains("minPrice", ignoreCase = true) ||
                            ex.message.contains("maxPrice", ignoreCase = true)
                        ) {
                            ApiCode.INVALID_PRICE
                        } else {
                            ApiCode.VALIDATION_ERROR
                        }
                    DomainErrorMapping(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, detailCode, field)
                }
                is InvalidMediaFileException ->
                    DomainErrorMapping(HttpStatus.BAD_REQUEST, ApiCode.INVALID_MEDIA_FILE, ApiCode.INVALID_MEDIA_FILE, "file")
                is DatasetImportException ->
                    DomainErrorMapping(HttpStatus.BAD_REQUEST, ApiCode.DATASET_IMPORT_ERROR)
                is InvalidCredentialsException ->
                    DomainErrorMapping(HttpStatus.UNAUTHORIZED, ApiCode.INVALID_CREDENTIALS)
                is InvalidTokenException ->
                    DomainErrorMapping(HttpStatus.UNAUTHORIZED, ApiCode.INVALID_TOKEN)
                is AccountInactiveException ->
                    DomainErrorMapping(HttpStatus.FORBIDDEN, ApiCode.ACCOUNT_INACTIVE)
                is UserNotFoundException ->
                    DomainErrorMapping(HttpStatus.NOT_FOUND, ApiCode.USER_NOT_FOUND)
                is RoleNotFoundException ->
                    DomainErrorMapping(HttpStatus.NOT_FOUND, ApiCode.ROLE_NOT_FOUND)
                is PermissionNotFoundException ->
                    DomainErrorMapping(HttpStatus.NOT_FOUND, ApiCode.PERMISSION_NOT_FOUND)
                is CategoryNotFoundException ->
                    DomainErrorMapping(HttpStatus.NOT_FOUND, ApiCode.CATEGORY_NOT_FOUND)
                is ProductNotFoundException ->
                    DomainErrorMapping(HttpStatus.NOT_FOUND, ApiCode.PRODUCT_NOT_FOUND)
                is UserAlreadyExistsException ->
                    DomainErrorMapping(HttpStatus.CONFLICT, ApiCode.USER_ALREADY_EXISTS, ApiCode.USER_ALREADY_EXISTS, "email")
                is DuplicateCategorySlugException ->
                    DomainErrorMapping(HttpStatus.CONFLICT, ApiCode.DUPLICATE_CATEGORY_SLUG, ApiCode.DUPLICATE_CATEGORY_SLUG, "slug")
                is DuplicateProductSkuException ->
                    DomainErrorMapping(HttpStatus.CONFLICT, ApiCode.DUPLICATE_PRODUCT_SKU, ApiCode.DUPLICATE_PRODUCT_SKU, "sku")
                is DuplicateProductSlugException ->
                    DomainErrorMapping(HttpStatus.CONFLICT, ApiCode.DUPLICATE_PRODUCT_SLUG, ApiCode.DUPLICATE_PRODUCT_SLUG, "slug")
                is ProductConcurrencyConflictException ->
                    DomainErrorMapping(
                        HttpStatus.CONFLICT,
                        ApiCode.PRODUCT_CONCURRENCY_CONFLICT,
                        ApiCode.PRODUCT_CONCURRENCY_CONFLICT,
                        "version",
                    )
                is ImageStorageException ->
                    DomainErrorMapping(HttpStatus.BAD_GATEWAY, ApiCode.IMAGE_STORAGE_ERROR)
            }

        private fun inferValidationField(message: String): String? =
            when {
                message.startsWith("minPrice cannot exceed maxPrice", ignoreCase = true) -> null
                message.startsWith("Cannot specify both categoryId and categorySlug", ignoreCase = true) -> null
                message.startsWith("minPrice", ignoreCase = true) -> "minPrice"
                message.startsWith("maxPrice", ignoreCase = true) -> "maxPrice"
                message.startsWith("minRating", ignoreCase = true) -> "minRating"
                message.contains("Invalid category ID", ignoreCase = true) -> "categoryId"
                message.startsWith("Invalid sortBy", ignoreCase = true) -> "sortBy"
                message.startsWith("Invalid sortDir", ignoreCase = true) -> "sortDir"
                message.contains("If-Match", ignoreCase = true) -> "If-Match"
                message.contains("Expected product version", ignoreCase = true) -> "version"
                message.contains("Product price", ignoreCase = true) || message.contains("Money amount", ignoreCase = true) -> "price"
                message.contains("Email", ignoreCase = true) -> "email"
                else -> null
            }

        private fun mapFieldErrorCode(fieldError: FieldError): String {
            if (fieldError.isBindingFailure) {
                return ApiCode.TYPE_MISMATCH
            }
            return mapCodes(fieldError.codes) ?: ApiCode.INVALID_VALUE
        }

        private fun resolveFieldErrorMessage(fieldError: FieldError): String {
            if (fieldError.isBindingFailure) {
                return "Invalid value for field '${fieldError.field}'."
            }
            return fieldError.defaultMessage ?: "Invalid value."
        }

        private fun mapCodes(codes: Array<String>?): String? {
            if (codes == null) return null
            for (rawCode in codes) {
                val baseName = rawCode.substringBefore(".")
                mapConstraintSimpleNameOrNull(baseName)?.let { return it }
            }
            return null
        }

        private fun mapConstraintSimpleName(simpleName: String?): String =
            mapConstraintSimpleNameOrNull(simpleName) ?: ApiCode.INVALID_VALUE

        private fun mapConstraintSimpleNameOrNull(name: String?): String? =
            when (name) {
                "NotBlank", "NotNull", "NotEmpty" -> ApiCode.REQUIRED_FIELD
                "Email" -> ApiCode.INVALID_EMAIL
                "ValidPassword" -> ApiCode.INVALID_PASSWORD
                "ValidPrice" -> ApiCode.INVALID_PRICE
                "Pattern" -> ApiCode.INVALID_FORMAT
                "Size", "Length" -> ApiCode.INVALID_LENGTH
                "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Negative" -> ApiCode.VALUE_OUT_OF_RANGE
                "typeMismatch" -> ApiCode.TYPE_MISMATCH
                else -> null
            }

        private fun extractViolationField(violation: ConstraintViolation<*>): String? {
            val leafName =
                violation.propertyPath
                    .mapNotNull { node -> node.name?.takeIf { it.isNotBlank() } }
                    .lastOrNull()
            return leafName
        }

        private data class DomainErrorMapping(
            val status: HttpStatus,
            val code: String,
            val detailCode: String = code,
            val field: String? = null,
        )
    }
