package org.shopflow.backend.domain.repository.auth

import org.shopflow.backend.domain.model.auth.Email
import org.shopflow.backend.domain.model.auth.User
import org.shopflow.backend.domain.model.auth.UserId

interface UserRepository {
    fun findByEmail(email: Email): User?

    fun findById(id: UserId): User?

    fun existsByEmail(email: Email): Boolean

    fun findExistingEmails(emails: Collection<String>): Set<String>

    fun findExistingIds(ids: Collection<UserId>): Set<UserId>

    fun save(user: User): User

    fun saveAll(users: Collection<User>): List<User>
}
