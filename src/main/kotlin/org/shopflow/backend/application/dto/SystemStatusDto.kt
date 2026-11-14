package org.shopflow.backend.application.dto

import java.time.Instant

data class SystemStatusDto(
    val status: String,
    val message: String,
    val timestamp: Instant,
)
