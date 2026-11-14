package org.shopflow.backend.domain.model.product

import org.shopflow.backend.domain.exception.DomainValidationException

enum class ProductStatus {
    ACTIVE,
    DRAFT,
    OUT_OF_STOCK,
    ARCHIVED,
    ;

    companion object {
        fun fromString(raw: String): ProductStatus =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: throw DomainValidationException("Invalid product status '$raw'.")
    }
}
