package org.shopflow.backend.infrastructure.storage.cloudinary

import org.shopflow.backend.domain.service.storage.CategoryBannerGeneratorPort
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class CategoryBannerGenerator : CategoryBannerGeneratorPort {
    override fun generateBannerPng(
        categoryName: String,
        categorySlug: String,
    ): ByteArray {
        val image = BufferedImage(BANNER_WIDTH, BANNER_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val (startColor, endColor) = resolvePalette(categorySlug)
            graphics.paint = GradientPaint(0f, 0f, startColor, BANNER_WIDTH.toFloat(), BANNER_HEIGHT.toFloat(), endColor)
            graphics.fillRect(0, 0, BANNER_WIDTH, BANNER_HEIGHT)

            graphics.color = Color(255, 255, 255, 36)
            graphics.fillOval(BANNER_WIDTH - 240, -90, 320, 320)
            graphics.fillOval(-110, BANNER_HEIGHT - 190, 280, 280)

            graphics.color = Color.WHITE
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 54)
            val titleMetrics = graphics.fontMetrics
            val titleX = ((BANNER_WIDTH - titleMetrics.stringWidth(categoryName)) / 2).coerceAtLeast(32)
            val titleY = BANNER_HEIGHT / 2
            graphics.drawString(categoryName, titleX, titleY)

            val subtitle = "SHOPFLOW OFFICIAL CATALOG"
            graphics.color = Color(240, 244, 248)
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 22)
            val subMetrics = graphics.fontMetrics
            val subX = ((BANNER_WIDTH - subMetrics.stringWidth(subtitle)) / 2).coerceAtLeast(32)
            graphics.drawString(subtitle, subX, titleY + 52)
        } finally {
            graphics.dispose()
        }

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    override fun defaultFallbackUrl(categorySlug: String): String =
        DEFAULT_FALLBACK_URLS[categorySlug.lowercase()]
            ?: "$FALLBACK_BASE_URL/category-$categorySlug.png"

    private fun resolvePalette(categorySlug: String): Pair<Color, Color> =
        CATEGORY_PALETTES[categorySlug.lowercase()]
            ?: (Color(30, 64, 175) to Color(15, 23, 42))

    companion object {
        private const val BANNER_WIDTH = 800
        private const val BANNER_HEIGHT = 600
        private const val FALLBACK_BASE_URL = "https://res.cloudinary.com/shopflow-demo/image/upload/v1/shopflow/categories"

        private val CATEGORY_PALETTES =
            mapOf(
                "electronics" to (Color(15, 23, 42) to Color(37, 99, 235)),
                "clothing" to (Color(88, 28, 135) to Color(219, 39, 119)),
                "books" to (Color(120, 53, 15) to Color(217, 119, 6)),
                "home-kitchen" to (Color(6, 78, 59) to Color(16, 185, 129)),
                "beauty" to (Color(136, 19, 55) to Color(244, 63, 94)),
                "sports" to (Color(12, 74, 110) to Color(14, 165, 233)),
                "automotive" to (Color(39, 39, 42) to Color(239, 68, 68)),
                "groceries" to (Color(20, 83, 45) to Color(101, 163, 13)),
                "pet-supplies" to (Color(67, 56, 202) to Color(139, 92, 246)),
                "toys" to (Color(180, 83, 9) to Color(234, 179, 8)),
            )

        private val DEFAULT_FALLBACK_URLS =
            CATEGORY_PALETTES.keys.associateWith { slug ->
                "$FALLBACK_BASE_URL/category-$slug.png"
            }
    }
}
