package org.shopflow.backend.domain.event.auth

import org.shopflow.backend.domain.event.DomainEvent
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.UserId
import java.time.Instant

data class UserLoggedInEvent(
    val userId: UserId,
    val email: Email,
    override val occurredAt: Instant,
) : DomainEvent
