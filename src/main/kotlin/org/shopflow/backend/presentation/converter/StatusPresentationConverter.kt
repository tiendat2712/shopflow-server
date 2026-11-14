package org.shopflow.backend.presentation.converter

import org.shopflow.backend.application.dto.SystemStatusDto
import org.shopflow.backend.presentation.dto.SystemStatusResponse

fun SystemStatusDto.toResponse(): SystemStatusResponse =
    SystemStatusResponse(
        status = status,
        message = message,
        timestamp = timestamp,
    )
