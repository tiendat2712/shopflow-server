package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException

@JvmInline
value class Sku(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw DomainValidationException("SKU cannot be blank.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("SKU must not exceed $MAX_LENGTH characters.")
        }
        if (!SKU_REGEX.matches(value)) {
            throw DomainValidationException("SKU must contain uppercase letters, numbers, underscores, or hyphens.")
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 100
        private val SKU_REGEX = Regex("^[A-Z0-9][A-Z0-9_-]*$")
        private val INVALID_CHARS_REGEX = Regex("[^A-Z0-9_-]+")

        fun of(raw: String): Sku {
            val normalized =
                raw
                    .trim()
                    .uppercase()
                    .replace(Regex("\\s+"), "-")
                    .replace(INVALID_CHARS_REGEX, "-")
                    .trim('-', '_')
                    .take(MAX_LENGTH)
                    .trimEnd('-', '_')
            return Sku(normalized)
        }
    }
}
