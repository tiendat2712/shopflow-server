package org.shopflow.backend.presentation.controller.product

import jakarta.validation.Valid
import org.shopflow.backend.application.query.product.ListCategoriesQuery
import org.shopflow.backend.application.query.product.ListProductsQuery
import org.shopflow.backend.application.usecase.product.CreateCategoryUseCase
import org.shopflow.backend.application.usecase.product.ListCategoriesUseCase
import org.shopflow.backend.application.usecase.product.ListProductsUseCase
import org.shopflow.backend.presentation.converter.toCommand
import org.shopflow.backend.presentation.converter.toPageResponse
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.product.CategoryResponse
import org.shopflow.backend.presentation.dto.product.CreateCategoryRequest
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/categories")
class CategoryController(
    private val listCategoriesUseCase: ListCategoriesUseCase,
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val listProductsUseCase: ListProductsUseCase,
) {
    @GetMapping
    fun listCategories(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "false") onlyActive: Boolean,
        @RequestParam(required = false) parentId: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "displayOrder") sortBy: String,
        @RequestParam(defaultValue = "asc") sortDir: String,
    ): PageResponse<CategoryResponse> =
        listCategoriesUseCase
            .execute(
                ListCategoriesQuery(
                    page = page,
                    size = size,
                    onlyActive = onlyActive,
                    parentId = parentId,
                    keyword = keyword,
                    sortBy = sortBy,
                    sortDir = sortDir,
                ),
            ).toPageResponse { it.toResponse() }

    @GetMapping("/{idOrSlug}/products")
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
    ): PageResponse<ProductSummaryResponse> {
        val trimmed = idOrSlug.trim()
        val isCanonicalUuid = CANONICAL_UUID_REGEX.matches(trimmed)
        return listProductsUseCase
            .execute(
                ListProductsQuery(
                    page = page,
                    size = size,
                    categoryId = if (isCanonicalUuid) trimmed else null,
                    categorySlug = if (isCanonicalUuid) null else trimmed,
                    keyword = keyword,
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    minRating = minRating,
                    status = status,
                    sortBy = sortBy,
                    sortDir = sortDir,
                ),
            ).toPageResponse { it.toResponse() }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(
        @Valid @RequestBody request: CreateCategoryRequest,
    ): CategoryResponse =
        createCategoryUseCase
            .execute(request.toCommand())
            .toResponse()

    companion object {
        private val CANONICAL_UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
