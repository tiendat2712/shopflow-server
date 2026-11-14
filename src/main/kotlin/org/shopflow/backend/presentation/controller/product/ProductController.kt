package org.shopflow.backend.presentation.controller.product

import jakarta.validation.Valid
import org.shopflow.backend.application.command.product.DeleteProductCommand
import org.shopflow.backend.application.query.product.GetProductDetailQuery
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.usecase.product.CreateProductUseCase
import org.shopflow.backend.application.usecase.product.DeleteProductUseCase
import org.shopflow.backend.application.usecase.product.GetProductDetailUseCase
import org.shopflow.backend.application.usecase.product.ListProductsUseCase
import org.shopflow.backend.application.usecase.product.UpdateProductUseCase
import org.shopflow.backend.presentation.converter.toCommand
import org.shopflow.backend.presentation.converter.toPageResponse
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.product.CreateProductRequest
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductDetailResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.shopflow.backend.presentation.dto.product.UpdateProductRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val listProductsUseCase: ListProductsUseCase,
    private val getProductDetailUseCase: GetProductDetailUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
) {
    @GetMapping
    fun listProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) categorySlug: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) minRating: BigDecimal?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String,
    ): PageResponse<ProductSummaryResponse> =
        listProductsUseCase
            .execute(
                ListProductsQuery(
                    page = page,
                    size = size,
                    categoryId = categoryId,
                    categorySlug = categorySlug,
                    keyword = keyword,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    minRating = minRating,
                    status = status,
                    sortBy = sortBy,
                    sortDir = sortDir,
                ),
            ).toPageResponse { it.toResponse() }

    @GetMapping("/{idOrSlug}")
    fun getProductDetail(
        @PathVariable idOrSlug: String,
    ): ProductDetailResponse =
        getProductDetailUseCase
            .execute(GetProductDetailQuery(idOrSlug))
            .toResponse()

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(
        @Valid @RequestBody request: CreateProductRequest,
    ): ProductDetailResponse =
        createProductUseCase
            .execute(request.toCommand())
            .toResponse()

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    fun updateProduct(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ProductDetailResponse =
        updateProductUseCase
            .execute(request.toCommand(id))
            .toResponse()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(
        @PathVariable id: String,
    ) {
        deleteProductUseCase.execute(DeleteProductCommand(id))
    }
}
