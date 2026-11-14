package org.shopflow.backend.application.query.product

import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto

interface ProductQueryService {
    fun listProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto>

    fun findProductDetail(idOrSlug: String): ProductDetailDto?
}
