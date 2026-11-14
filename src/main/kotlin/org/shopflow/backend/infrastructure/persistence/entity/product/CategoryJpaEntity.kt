package org.shopflow.backend.infrastructure.persistence.entity.product

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity

@Entity
@Table(
    name = "categories",
    indexes = [
        Index(name = "uk_categories_slug", columnList = "slug", unique = true),
        Index(name = "idx_categories_parent_order", columnList = "parent_id, display_order"),
    ],
)
class CategoryJpaEntity(
    id: String,
    @field:Column(name = "name", nullable = false, length = 120)
    var name: String,
    @field:Column(name = "slug", nullable = false, unique = true, length = 140)
    var slug: String,
    @field:Column(name = "parent_id", length = 36)
    var parentId: String? = null,
    @field:Column(name = "description", length = 500)
    var description: String? = null,
    @field:Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
    @field:Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseJpaEntity(id) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CategoryJpaEntity) return false
        return getId() == other.getId()
    }

    override fun hashCode(): Int = getId().hashCode()
}
