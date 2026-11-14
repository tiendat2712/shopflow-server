package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException
import java.text.Normalizer

@JvmInline
value class Slug(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw DomainValidationException("Slug cannot be blank.")
        }
        if (value.length > MAX_LENGTH) {
            throw DomainValidationException("Slug must not exceed $MAX_LENGTH characters.")
        }
        if (!SLUG_REGEX.matches(value)) {
            throw DomainValidationException("Slug must contain lowercase alphanumeric characters and hyphens only.")
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 255
        const val CATEGORY_MAX_LENGTH = 140
        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        fun of(
            raw: String,
            maxLength: Int = MAX_LENGTH,
        ): Slug {
            val normalized =
                Normalizer
                    .normalize(raw.trim().replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                    .replace(DIACRITICS_REGEX, "")
                    .lowercase()
                    .replace(NON_ALNUM_REGEX, "-")
                    .trim('-')
                    .take(maxLength)
                    .trimEnd('-')

            return Slug(normalized)
        }
    }
}
