package org.shopflow.backend.infrastructure.persistence.repository.auth

import org.shopflow.backend.domain.exception.RoleNotFoundException
import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.Role
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId
import org.shopflow.backend.domain.repository.auth.UserRepository
import org.shopflow.backend.infrastructure.persistence.entity.auth.RoleJpaEntity
import org.shopflow.backend.infrastructure.persistence.jpa.auth.RoleJpaRepository
import org.shopflow.backend.infrastructure.persistence.jpa.auth.UserJpaRepository
import org.shopflow.backend.infrastructure.persistence.mapper.auth.UserPersistenceMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserJpaRepository,
    private val roleJpaRepository: RoleJpaRepository,
    private val mapper: UserPersistenceMapper,
) : UserRepository {
    override fun findByEmail(email: Email): User? = jpaRepository.findByEmail(email.value)?.let(mapper::toDomain)

    override fun findById(id: UserId): User? = jpaRepository.findById(id.value.toString()).orElse(null)?.let(mapper::toDomain)

    override fun existsByEmail(email: Email): Boolean = jpaRepository.existsByEmail(email.value)

    override fun findExistingEmails(emails: Collection<String>): Set<String> {
        val normalized = emails.mapTo(hashSetOf()) { it.trim().lowercase() }
        if (normalized.isEmpty()) return emptySet()
        return jpaRepository.findExistingEmails(normalized)
    }

    override fun findExistingIds(ids: Collection<UserId>): Set<UserId> {
        if (ids.isEmpty()) return emptySet()
        val rawIds = ids.map { it.value.toString() }
        return jpaRepository.findExistingIds(rawIds).mapTo(hashSetOf()) { UserId.fromString(it) }
    }

    override fun save(user: User): User {
        val resolvedRoles = resolveRoles(user.roles)
        val existing = jpaRepository.findById(user.id.value.toString()).orElse(null)
        val entity =
            if (existing != null) {
                existing.apply {
                    email = user.email.value
                    hashedPassword = user.hashedPassword.value
                    status = user.status
                    fullName = user.fullName
                    phoneNumber = user.phoneNumber
                    city = user.city
                    address = user.address
                    avatarUrl = user.avatarUrl
                    roles.clear()
                    roles.addAll(resolvedRoles)
                }
            } else {
                mapper.toEntity(user, resolvedRoles)
            }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity))
    }

    @Transactional
    override fun saveAll(users: Collection<User>): List<User> {
        if (users.isEmpty()) return emptyList()
        val allRoleIds = users.flatMapTo(linkedSetOf()) { user -> user.roles.map { it.id.value.toString() } }
        val rolesById =
            if (allRoleIds.isEmpty()) {
                emptyMap()
            } else {
                roleJpaRepository.findAllById(allRoleIds).associateBy { it.getId() }
            }
        val entities =
            users.map { user ->
                val resolvedRoles =
                    user.roles.mapTo(linkedSetOf()) { role ->
                        rolesById[role.id.value.toString()]
                            ?: throw RoleNotFoundException("Role '${role.name}' not found.")
                    }
                mapper.toEntity(user, resolvedRoles)
            }
        return jpaRepository.saveAllAndFlush(entities).map(mapper::toDomain)
    }

    private fun resolveRoles(roles: Set<Role>): MutableSet<RoleJpaEntity> {
        if (roles.isEmpty()) return mutableSetOf()
        val requestedIds = roles.map { it.id.value.toString() }
        val foundById = roleJpaRepository.findAllById(requestedIds).associateBy { it.getId() }
        val resolved = linkedSetOf<RoleJpaEntity>()
        for (role in roles) {
            val found =
                foundById[role.id.value.toString()]
                    ?: throw RoleNotFoundException("Role '${role.name}' not found.")
            resolved.add(found)
        }
        return resolved
    }
}
