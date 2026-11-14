package org.shopflow.backend.domain.model.auth

import org.shopflow.backend.domain.exception.DomainValidationException

@JvmInline
value class Email(
    val value: String,
) {
    init {
        if (value.isBlank() || !value.contains("@")) {
            throw DomainValidationException("Invalid email format.")
        }
    }

    override fun toString(): String = value

    companion object {
        fun of(raw: String): Email = Email(raw.trim())
    }
}
