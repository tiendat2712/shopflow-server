package org.shopflow.backend.application.usecase.auth

import org.shopflow.backend.application.dto.SystemStatusDto
import org.shopflow.backend.application.query.auth.GetSystemStatusQuery
import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Service

@Service
class GetSystemStatusUseCase(
    private val timeProvider: TimeProvider,
) {
    fun execute(query: GetSystemStatusQuery): SystemStatusDto =
        SystemStatusDto(
            status = "UP",
            message = "Everything cool!",
            timestamp = timeProvider.now(),
        )
}
