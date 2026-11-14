package org.shopflow.backend.presentation.converter

import org.shopflow.backend.application.command.dataset.ImportKaggleDatasetCommand
import org.shopflow.backend.application.command.product.CreateCategoryCommand
import org.shopflow.backend.application.command.product.CreateProductCommand
import org.shopflow.backend.application.command.product.UpdateProductCommand
import org.shopflow.backend.application.dto.dataset.DatasetImportResultDto
import org.shopflow.backend.application.dto.product.CategoryDto
import org.shopflow.backend.application.dto.product.PageResponseDto
import org.shopflow.backend.application.dto.product.ProductDetailDto
import org.shopflow.backend.application.dto.product.ProductSummaryDto
import org.shopflow.backend.presentation.dto.dataset.DatasetImportResponse
import org.shopflow.backend.presentation.dto.dataset.ImportKaggleDatasetRequest
import org.shopflow.backend.presentation.dto.product.CategoryResponse
import org.shopflow.backend.presentation.dto.product.CreateCategoryRequest
import org.shopflow.backend.presentation.dto.product.CreateProductRequest
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductDetailResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.shopflow.backend.presentation.dto.product.UpdateProductRequest

fun CreateCategoryRequest.toCommand(): CreateCategoryCommand =
    CreateCategoryCommand(
        name = name,
        slug = slug,
        parentId = parentId,
        description = description,
        displayOrder = displayOrder,
        isActive = isActive,
    )

fun CategoryDto.toResponse(): CategoryResponse =
    CategoryResponse(
        id = id,
        name = name,
        slug = slug,
        parentId = parentId,
        description = description,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt,
    )

fun CreateProductRequest.toCommand(): CreateProductCommand =
    CreateProductCommand(
        categoryId = categoryId,
        name = name,
        slug = slug,
        sku = sku,
        description = description,
        price = price,
        stockQuantity = stockQuantity,
        imageUrl = imageUrl,
        status = status,
    )

fun UpdateProductRequest.toCommand(productId: String): UpdateProductCommand =
    UpdateProductCommand(
        productId = productId,
        categoryId = categoryId,
        name = name,
        slug = slug,
        sku = sku,
        description = description,
        price = price,
        stockQuantity = stockQuantity,
        imageUrl = imageUrl,
        status = status,
    )

fun ProductSummaryDto.toResponse(): ProductSummaryResponse =
    ProductSummaryResponse(
        id = id,
        categoryId = categoryId,
        categoryName = categoryName,
        name = name,
        slug = slug,
        sku = sku,
        price = price,
        stockQuantity = stockQuantity,
        averageRating = averageRating,
        reviewCount = reviewCount,
        imageUrl = imageUrl,
        status = status,
    )

fun ProductDetailDto.toResponse(): ProductDetailResponse =
    ProductDetailResponse(
        id = id,
        categoryId = categoryId,
        categoryName = categoryName,
        categorySlug = categorySlug,
        name = name,
        slug = slug,
        sku = sku,
        description = description,
        price = price,
        stockQuantity = stockQuantity,
        averageRating = averageRating,
        reviewCount = reviewCount,
        imageUrl = imageUrl,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun <T, R> PageResponseDto<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        items = items.map(mapper),
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
    )

fun ImportKaggleDatasetRequest.toCommand(): ImportKaggleDatasetCommand =
    ImportKaggleDatasetCommand(
        forceDownload = forceDownload,
        uploadCategoryPlaceholders = uploadCategoryPlaceholders,
        refreshExistingProductImages = refreshExistingProductImages,
        maxUsers = maxUsers,
        maxProducts = maxProducts,
    )

fun DatasetImportResultDto.toResponse(): DatasetImportResponse =
    DatasetImportResponse(
        sourceDirectory = sourceDirectory,
        downloadedFromRemote = downloadedFromRemote,
        cloudinaryLiveUploadUsed = cloudinaryLiveUploadUsed,
        usersInserted = usersInserted,
        usersSkipped = usersSkipped,
        categoriesInserted = categoriesInserted,
        categoriesExisting = categoriesExisting,
        productsInserted = productsInserted,
        productsSkipped = productsSkipped,
        productsImageUpdated = productsImageUpdated,
        ordersInserted = ordersInserted,
        ordersSkipped = ordersSkipped,
        orderItemsInserted = orderItemsInserted,
        orderItemsSkipped = orderItemsSkipped,
        reviewsInserted = reviewsInserted,
        reviewsSkipped = reviewsSkipped,
        productReviewCountsUpdated = productReviewCountsUpdated,
        userEventsInserted = userEventsInserted,
        userEventsSkipped = userEventsSkipped,
        categoryImageUrls = categoryImageUrls,
        stagedCsvFiles = stagedCsvFiles,
        durationMs = durationMs,
    )
