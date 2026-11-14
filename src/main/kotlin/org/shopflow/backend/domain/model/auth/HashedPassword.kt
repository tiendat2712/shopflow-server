package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException

@JvmInline
value class HashedPassword(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw DomainValidationException("Hashed password cannot be blank.")
        }
    }

    override fun toString(): String = "[PROTECTED]"
}
