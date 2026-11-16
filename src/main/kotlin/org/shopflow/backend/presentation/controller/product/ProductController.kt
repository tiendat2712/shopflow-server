package org.shopflow.backend.presentation.controller.product

import jakarta.validation.Valid
import org.shopflow.backend.application.command.product.DeleteProductCommand
import org.shopflow.backend.application.command.product.PublishProductCommand
import org.shopflow.backend.application.command.product.RestoreProductCommand
import org.shopflow.backend.application.query.product.GetProductDetailQuery
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.usecase.product.CreateProductUseCase
import org.shopflow.backend.application.usecase.product.DeleteProductUseCase
import org.shopflow.backend.application.usecase.product.GetProductDetailUseCase
import org.shopflow.backend.application.usecase.product.ListProductsUseCase
import org.shopflow.backend.application.usecase.product.PublishProductUseCase
import org.shopflow.backend.application.usecase.product.RestoreProductUseCase
import org.shopflow.backend.application.usecase.product.UpdateProductUseCase
import org.shopflow.backend.presentation.converter.resolveExpectedProductVersion
import org.shopflow.backend.presentation.converter.toCommand
import org.shopflow.backend.presentation.converter.toPageResponse
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.ApiCode
import org.shopflow.backend.presentation.dto.ApiResponse
import org.shopflow.backend.presentation.dto.product.CreateProductRequest
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductDetailResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.shopflow.backend.presentation.dto.product.UpdateProductRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
    private val publishProductUseCase: PublishProductUseCase,
    private val restoreProductUseCase: RestoreProductUseCase,
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
    ): ApiResponse<PageResponse<ProductSummaryResponse>> =
        ApiResponse.ok(
            code = ApiCode.PRODUCT_LIST_SUCCESS,
            message = "Products retrieved successfully.",
            result =
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
                    ).toPageResponse { it.toResponse() },
        )

    @GetMapping("/{idOrSlug}")
    fun getProductDetail(
        @PathVariable idOrSlug: String,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val response =
            getProductDetailUseCase
                .execute(GetProductDetailQuery(idOrSlug))
                .toResponse()
        return ResponseEntity
            .ok()
            .eTag(response.version.toString())
            .body(
                ApiResponse.ok(
                    code = ApiCode.PRODUCT_DETAIL_SUCCESS,
                    message = "Product retrieved successfully.",
                    result = response,
                ),
            )
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    fun createProduct(
        @Valid @RequestBody request: CreateProductRequest,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val response =
            createProductUseCase
                .execute(request.toCommand())
                .toResponse()
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .eTag(response.version.toString())
            .body(
                ApiResponse.created(
                    code = ApiCode.PRODUCT_CREATE_SUCCESS,
                    message = "Product created successfully.",
                    result = response,
                ),
            )
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    fun updateProduct(
        @PathVariable id: String,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) ifMatch: String? = null,
        @RequestParam(required = false) version: Long? = null,
        @Valid @RequestBody request: UpdateProductRequest,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val expectedVersion =
            resolveExpectedProductVersion(
                ifMatchHeader = ifMatch,
                bodyVersion = request.version,
                queryVersion = version,
            )
        val response =
            updateProductUseCase
                .execute(request.toCommand(id, expectedVersion))
                .toResponse()
        return ResponseEntity
            .ok()
            .eTag(response.version.toString())
            .body(
                ApiResponse.ok(
                    code = ApiCode.PRODUCT_UPDATE_SUCCESS,
                    message = "Product updated successfully.",
                    result = response,
                ),
            )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(
        @PathVariable id: String,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) ifMatch: String? = null,
        @RequestParam(required = false) version: Long? = null,
    ) {
        val expectedVersion =
            resolveExpectedProductVersion(
                ifMatchHeader = ifMatch,
                queryVersion = version,
            )
        deleteProductUseCase.execute(DeleteProductCommand(id, expectedVersion))
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    fun publishProduct(
        @PathVariable id: String,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) ifMatch: String? = null,
        @RequestParam(required = false) version: Long? = null,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val expectedVersion =
            resolveExpectedProductVersion(
                ifMatchHeader = ifMatch,
                queryVersion = version,
            )
        val response =
            publishProductUseCase
                .execute(PublishProductCommand(id, expectedVersion))
                .toResponse()
        return ResponseEntity
            .ok()
            .eTag(response.version.toString())
            .body(
                ApiResponse.ok(
                    code = ApiCode.PRODUCT_PUBLISH_SUCCESS,
                    message = "Product published successfully.",
                    result = response,
                ),
            )
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    fun restoreProduct(
        @PathVariable id: String,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) ifMatch: String? = null,
        @RequestParam(required = false) version: Long? = null,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val expectedVersion =
            resolveExpectedProductVersion(
                ifMatchHeader = ifMatch,
                queryVersion = version,
            )
        val response =
            restoreProductUseCase
                .execute(RestoreProductCommand(id, expectedVersion))
                .toResponse()
        return ResponseEntity
            .ok()
            .eTag(response.version.toString())
            .body(
                ApiResponse.ok(
                    code = ApiCode.PRODUCT_RESTORE_SUCCESS,
                    message = "Product restored successfully.",
                    result = response,
                ),
            )
    }
}
