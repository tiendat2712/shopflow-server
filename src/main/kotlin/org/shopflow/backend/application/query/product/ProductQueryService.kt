package org.shopflow.backend.application.query.product

import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto

interface ProductQueryService {
    fun listPublicProducts(query: ListProductsQuery): PageResponseDto<ProductSummaryDto>

    fun findPublicProductDetail(idOrSlug: String): ProductDetailDto?

    fun listAdminProducts(query: ListAdminProductsQuery): PageResponseDto<ProductSummaryDto>

    fun findAdminProductDetail(idOrSlug: String): ProductDetailDto?
}
