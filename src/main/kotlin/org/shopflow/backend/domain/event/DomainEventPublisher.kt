package org.shopflow.backend.domain.event

fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
