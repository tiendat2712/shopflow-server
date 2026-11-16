package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException

enum class ProductStatus {
    ACTIVE,
    DRAFT,
    OUT_OF_STOCK,
    ARCHIVED,
    ;

    val isPubliclyVisible: Boolean
        get() = this in PUBLIC_STATUSES

    fun canTransitionTo(target: ProductStatus): Boolean =
        when (this) {
            DRAFT -> target == ACTIVE || target == ARCHIVED
            ACTIVE -> target == OUT_OF_STOCK || target == ARCHIVED
            OUT_OF_STOCK -> target == ACTIVE || target == ARCHIVED
            ARCHIVED -> target == DRAFT
        }

    companion object {
        val PUBLIC_STATUSES: Set<ProductStatus> = setOf(ACTIVE, OUT_OF_STOCK)

        fun fromString(raw: String): ProductStatus =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: throw DomainValidationException("Invalid product status '$raw'.")

        fun fromPublicFilter(raw: String): ProductStatus {
            val trimmed = raw.trim()
            if (trimmed.equals("ALL", ignoreCase = true)) {
                throw DomainValidationException(
                    "Product status '$trimmed' is not available on public storefront. Allowed: ACTIVE, OUT_OF_STOCK.",
                )
            }
            val parsed = fromString(trimmed)
            if (!parsed.isPubliclyVisible) {
                throw DomainValidationException(
                    "Product status '$trimmed' is not available on public storefront. Allowed: ACTIVE, OUT_OF_STOCK.",
                )
            }
            return parsed
        }
    }
}
