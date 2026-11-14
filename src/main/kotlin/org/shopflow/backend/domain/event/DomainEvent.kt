package org.shopflow.backend.domain.event

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
