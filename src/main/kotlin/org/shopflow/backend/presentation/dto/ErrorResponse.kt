package org.shopflow.backend.presentation.dto

import java.time.Instant

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val errors: List<String> = emptyList(),
    val timestamp: Instant = Instant.now(),
)
