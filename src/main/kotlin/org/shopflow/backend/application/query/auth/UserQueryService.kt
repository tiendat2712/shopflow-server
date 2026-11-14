package org.shopflow.backend.application.query.auth

import org.shopflow.backend.application.dto.auth.UserProfileDto
import org.shopflow.backend.domain.model.auth.UserId

/**
 * CQRS read-side port for querying user profile views directly.
 */
interface UserQueryService {
    fun findUserProfileById(userId: UserId): UserProfileDto?
}
