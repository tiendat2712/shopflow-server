package org.shopflow.backend.presentation.controller

import org.shopflow.backend.application.query.auth.GetSystemStatusQuery
import org.shopflow.backend.application.usecase.auth.GetSystemStatusUseCase
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.ApiCode
import org.shopflow.backend.presentation.dto.ApiResponse
import org.shopflow.backend.presentation.dto.SystemStatusResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController(
    private val getSystemStatusUseCase: GetSystemStatusUseCase,
) {
    @GetMapping("/", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun rootStatus(): String = getSystemStatusUseCase.execute(GetSystemStatusQuery).message

    @GetMapping("/api/status")
    fun apiStatus(): ApiResponse<SystemStatusResponse> =
        ApiResponse.ok(
            code = ApiCode.SYSTEM_STATUS_SUCCESS,
            message = "System status retrieved successfully.",
            result = getSystemStatusUseCase.execute(GetSystemStatusQuery).toResponse(),
        )
}
