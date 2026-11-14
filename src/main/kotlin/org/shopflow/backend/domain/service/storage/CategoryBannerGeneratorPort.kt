package org.shopflow.backend.domain.service.storage

interface CategoryBannerGeneratorPort {
    fun generateBannerPng(
        categoryName: String,
        categorySlug: String,
    ): ByteArray

    fun defaultFallbackUrl(categorySlug: String): String
}
