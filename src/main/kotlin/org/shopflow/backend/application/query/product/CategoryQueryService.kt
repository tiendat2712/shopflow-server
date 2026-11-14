package org.shopflow.backend.application.query.product

import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto

interface CategoryQueryService {
    fun listCategories(query: ListCategoriesQuery): PageResponseDto<CategoryDto>
}
