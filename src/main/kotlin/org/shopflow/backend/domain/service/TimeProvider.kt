package org.shopflow.backend.domain.service

import java.time.Instant

fun interface TimeProvider {
    fun now(): Instant
}
