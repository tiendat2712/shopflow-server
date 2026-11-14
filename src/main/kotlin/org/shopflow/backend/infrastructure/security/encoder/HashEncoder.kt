package org.shopflow.backend.infrastructure.security.encoder

import org.shopflow.backend.domain.model.auth.HashedPassword
import org.shopflow.backend.domain.service.auth.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class HashEncoder : PasswordHasher {
    private val bcrypt = BCryptPasswordEncoder()

    override fun encode(rawPassword: String): HashedPassword {
        val encoded =
            requireNotNull(bcrypt.encode(rawPassword)) {
                "Failed to encode password."
            }
        return HashedPassword(encoded)
    }

    override fun matches(
        rawPassword: String,
        hashedPassword: HashedPassword,
    ): Boolean = bcrypt.matches(rawPassword, hashedPassword.value)
}
