package org.shopflow.backend.infrastructure.config

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.event.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
