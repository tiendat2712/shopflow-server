package org.shopflow.backend.presentation.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.HttpStatus
import java.time.Instant

data class ApiErrorDetail(
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val field: String?,
    val code: String,
    val message: String,
)

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val status: Int,
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val result: T?,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val errors: List<ApiErrorDetail>? = null,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val timestamp: Instant? = null,
) {
    companion object {
        fun <T> ok(
            code: String,
            message: String,
            result: T,
        ): ApiResponse<T> =
            ApiResponse(
                code = code,
                message = message,
                status = HttpStatus.OK.value(),
                result = result,
            )

        fun <T> created(
            code: String,
            message: String,
            result: T,
        ): ApiResponse<T> =
            ApiResponse(
                code = code,
                message = message,
                status = HttpStatus.CREATED.value(),
                result = result,
            )

        fun <T> error(
            code: String,
            message: String,
            status: Int,
            errors: List<ApiErrorDetail> = listOf(ApiErrorDetail(field = null, code = code, message = message)),
            timestamp: Instant = Instant.now(),
        ): ApiResponse<T> =
            ApiResponse(
                code = code,
                message = message,
                status = status,
                result = null,
                errors = errors,
                timestamp = timestamp,
            )
    }
}
