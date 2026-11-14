package org.shopflow.backend.presentation.dto

import java.time.Instant

data class SystemStatusResponse(
    val status: String,
    val message: String,
    val timestamp: Instant,
)
