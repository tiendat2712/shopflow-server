package org.shopflow.backend.infrastructure.security

import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.service.auth.UserSecuritySnapshot
import org.shopflow.backend.domain.service.auth.UserSecurityStatePort
import org.shopflow.backend.infrastructure.persistence.jpa.auth.UserJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JpaUserSecurityStateAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UserSecurityStatePort {
    @Transactional(readOnly = true)
    override fun findByUserId(userId: UserId): UserSecuritySnapshot? {
        val entity = userJpaRepository.findById(userId.value.toString()).orElse(null) ?: return null
        val roles = entity.roles.mapTo(linkedSetOf()) { it.name }
        val permissions =
            entity.roles.flatMapTo(linkedSetOf()) { role ->
                role.permissions.map { it.code }
            }
        return UserSecuritySnapshot(
            userId = UserId.fromString(entity.getId()),
            status = entity.status,
            roles = roles,
            permissions = permissions,
        )
    }
}
