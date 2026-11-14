package org.shopflow.backend.domain.service.auth

import org.shopflow.backend.domain.model.auth.HashedPassword

interface PasswordHasher {
    fun encode(rawPassword: String): HashedPassword

    fun matches(
        rawPassword: String,
        hashedPassword: HashedPassword,
    ): Boolean
}
