package org.shopflow.backend.presentation.controller

import org.shopflow.backend.application.query.auth.GetSystemStatusQuery
import org.shopflow.backend.application.usecase.auth.GetSystemStatusUseCase
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.SystemStatusResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController(
    private val getSystemStatusUseCase: GetSystemStatusUseCase,
) {
    @GetMapping("/")
    fun rootStatus(): String = getSystemStatusUseCase.execute(GetSystemStatusQuery).message

    @GetMapping("/api/status")
    fun apiStatus(): SystemStatusResponse = getSystemStatusUseCase.execute(GetSystemStatusQuery).toResponse()
}
