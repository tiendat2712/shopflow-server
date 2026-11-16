package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.query.product.GetAdminProductDetailQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetAdminProductDetailUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: GetAdminProductDetailQuery): ProductDetailDto {
        val key = query.idOrSlug.trim()
        if (key.isEmpty()) {
            throw ProductNotFoundException()
        }
        return productQueryService.findAdminProductDetail(key)
            ?: throw ProductNotFoundException()
    }
}
