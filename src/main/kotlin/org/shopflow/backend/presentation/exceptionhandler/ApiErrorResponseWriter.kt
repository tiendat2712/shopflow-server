package org.shopflow.backend.presentation.exceptionhandler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.shopflow.backend.presentation.dto.ApiErrorDetail
import org.shopflow.backend.presentation.dto.ApiResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApiErrorResponseWriter(
    private val objectMapper: ObjectMapper,
) {
    fun toResponseEntity(
        status: HttpStatus,
        code: String,
        message: String,
        errors: List<ApiErrorDetail> = listOf(ApiErrorDetail(field = null, code = code, message = message)),
        headers: HttpHeaders? = null,
        request: HttpServletRequest? = null,
    ): ResponseEntity<Any> {
        val builder = ResponseEntity.status(status)
        if (headers != null && !headers.isEmpty) {
            builder.headers(headers)
        }
        if (!canAcceptJson(request)) {
            return builder.build()
        }
        builder.contentType(MediaType.APPLICATION_JSON)
        return builder.body(
            ApiResponse.error<Nothing>(
                code = code,
                message = message,
                status = status.value(),
                errors = errors,
            ),
        )
    }

    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
        errors: List<ApiErrorDetail> = listOf(ApiErrorDetail(field = null, code = code, message = message)),
    ) {
        if (response.isCommitted) return
        response.status = status.value()
        if (!canAcceptJson(request)) {
            return
        }
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body =
            ApiResponse.error<Nothing>(
                code = code,
                message = message,
                status = status.value(),
                errors = errors,
            )
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    fun canAcceptJson(request: HttpServletRequest?): Boolean {
        if (request == null) return true
        val rawHeaders =
            request
                .getHeaders(HttpHeaders.ACCEPT)
                ?.toList()
                ?.filter { it.isNotBlank() }
                .orEmpty()
        if (rawHeaders.isEmpty()) return true
        return try {
            MediaType.parseMediaTypes(rawHeaders).any { it.isCompatibleWith(MediaType.APPLICATION_JSON) }
        } catch (_: InvalidMediaTypeException) {
            false
        }
    }
}
