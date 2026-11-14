package org.shopflow.backend.application.usecase.product

import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.query.product.GetProductDetailQuery
import org.shopflow.backend.application.query.product.ProductQueryService
import org.shopflow.backend.domain.exception.ProductNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetProductDetailUseCase(
    private val productQueryService: ProductQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(query: GetProductDetailQuery): ProductDetailDto {
        val key = query.idOrSlug.trim()
        if (key.isEmpty()) {
            throw ProductNotFoundException()
        }
        return productQueryService.findProductDetail(key)
            ?: throw ProductNotFoundException()
    }
}
