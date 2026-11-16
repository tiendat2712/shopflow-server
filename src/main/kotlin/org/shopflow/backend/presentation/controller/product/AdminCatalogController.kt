package org.shopflow.backend.presentation.controller.product

import org.shopflow.backend.application.query.product.GetAdminProductDetailQuery
import org.shopflow.backend.application.query.product.ListAdminCategoriesQuery
import org.shopflow.backend.application.query.product.ListAdminProductsQuery
import org.shopflow.backend.application.usecase.product.GetAdminProductDetailUseCase
import org.shopflow.backend.application.usecase.product.ListAdminCategoriesUseCase
import org.shopflow.backend.application.usecase.product.ListAdminProductsUseCase
import org.shopflow.backend.presentation.converter.resolveCategoryIdOrSlug
import org.shopflow.backend.presentation.converter.toPageResponse
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.ApiCode
import org.shopflow.backend.presentation.dto.ApiResponse
import org.shopflow.backend.presentation.dto.product.CategoryResponse
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductDetailResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
class AdminCatalogController(
    private val listAdminProductsUseCase: ListAdminProductsUseCase,
    private val getAdminProductDetailUseCase: GetAdminProductDetailUseCase,
    private val listAdminCategoriesUseCase: ListAdminCategoriesUseCase,
) {
    @GetMapping("/products")
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
            code = ApiCode.ADMIN_PRODUCT_LIST_SUCCESS,
            message = "Admin products retrieved successfully.",
            result =
                listAdminProductsUseCase
                    .execute(
                        ListAdminProductsQuery(
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

    @GetMapping("/products/{idOrSlug}")
    fun getProductDetail(
        @PathVariable idOrSlug: String,
    ): ResponseEntity<ApiResponse<ProductDetailResponse>> {
        val response =
            getAdminProductDetailUseCase
                .execute(GetAdminProductDetailQuery(idOrSlug))
                .toResponse()
        return ResponseEntity
            .ok()
            .eTag(response.version.toString())
            .body(
                ApiResponse.ok(
                    code = ApiCode.ADMIN_PRODUCT_DETAIL_SUCCESS,
                    message = "Admin product retrieved successfully.",
                    result = response,
                ),
            )
    }

    @GetMapping("/categories")
    fun listCategories(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) isActive: Boolean?,
        @RequestParam(required = false) onlyActive: Boolean?,
        @RequestParam(required = false) parentId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "displayOrder") sortBy: String,
        @RequestParam(defaultValue = "asc") sortDir: String,
    ): ApiResponse<PageResponse<CategoryResponse>> {
        val resolvedIsActive = isActive ?: if (onlyActive == true) true else null
        return ApiResponse.ok(
            code = ApiCode.ADMIN_CATEGORY_LIST_SUCCESS,
            message = "Admin categories retrieved successfully.",
            result =
                listAdminCategoriesUseCase
                    .execute(
                        ListAdminCategoriesQuery(
                            page = page,
                            size = size,
                            isActive = resolvedIsActive,
                            parentId = parentId,
                            keyword = keyword,
                            sortBy = sortBy,
                            sortDir = sortDir,
                        ),
                    ).toPageResponse { it.toResponse() },
        )
    }

    @GetMapping("/categories/{idOrSlug}/products")
    fun listCategoryProducts(
        @PathVariable idOrSlug: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) minRating: BigDecimal?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String,
    ): ApiResponse<PageResponse<ProductSummaryResponse>> {
        val (categoryId, categorySlug) = resolveCategoryIdOrSlug(idOrSlug)
        return ApiResponse.ok(
            code = ApiCode.ADMIN_CATEGORY_PRODUCT_LIST_SUCCESS,
            message = "Admin category products retrieved successfully.",
            result =
                listAdminProductsUseCase
                    .execute(
                        ListAdminProductsQuery(
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
    }
}
