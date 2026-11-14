package org.shopflow.backend.infrastructure.persistence.jpa.analytics

import org.shopflow.backend.infrastructure.persistence.entity.analytics.UserEventJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserEventJpaRepository : JpaRepository<UserEventJpaEntity, String>
