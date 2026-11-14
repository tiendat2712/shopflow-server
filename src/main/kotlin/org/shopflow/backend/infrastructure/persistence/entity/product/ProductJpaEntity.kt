package org.shopflow.backend.infrastructure.persistence.entity.product

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.domain.model.product.ProductStatus
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import java.math.BigDecimal

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "uk_products_slug", columnList = "slug", unique = true),
        Index(name = "uk_products_sku", columnList = "sku", unique = true),
        Index(name = "idx_products_category_status", columnList = "category_id, status"),
        Index(name = "idx_products_price", columnList = "price"),
        Index(name = "idx_products_rating", columnList = "average_rating"),
    ],
)
class ProductJpaEntity(
    id: String,
    @field:Column(name = "category_id", nullable = false, length = 36)
    var categoryId: String,
    @field:Column(name = "name", nullable = false, length = 255)
    var name: String,
    @field:Column(name = "slug", nullable = false, unique = true, length = 255)
    var slug: String,
    @field:Column(name = "sku", nullable = false, unique = true, length = 100)
    var sku: String,
    @field:Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
    @field:Column(name = "price", nullable = false, precision = 15, scale = 2)
    var price: BigDecimal,
    @field:Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int = 0,
    @field:Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    var averageRating: BigDecimal = BigDecimal.ZERO,
    @field:Column(name = "review_count", nullable = false)
    var reviewCount: Int = 0,
    @field:Column(name = "image_url", length = 500)
    var imageUrl: String? = null,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 32)
    var status: ProductStatus = ProductStatus.ACTIVE,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
