package org.shopflow.backend.domain.service.auth

import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.model.auth.UserStatus

enum class AuthFailureReason {
    INVALID_TOKEN,
    ACCOUNT_INACTIVE,
    ACCOUNT_BANNED,
}

data class UserSecuritySnapshot(
    val userId: UserId,
    val status: UserStatus,
    val roles: Set<String>,
    val permissions: Set<String>,
) {
    fun isActive(): Boolean = status == UserStatus.ACTIVE
}

interface UserSecurityStatePort {
    fun findByUserId(userId: UserId): UserSecuritySnapshot?
}
