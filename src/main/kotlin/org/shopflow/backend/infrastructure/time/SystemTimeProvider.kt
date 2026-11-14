package org.shopflow.backend.infrastructure.time

import org.shopflow.backend.domain.service.TimeProvider
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
}
