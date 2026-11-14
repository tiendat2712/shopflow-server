package org.shopflow.backend.infrastructure.persistence.jpa.review

import org.shopflow.backend.infrastructure.persistence.entity.review.ReviewJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewJpaRepository : JpaRepository<ReviewJpaEntity, String>
