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
import org.shopflow.backend.domain.exception.DomainValidationException
import org.shopflow.backend.presentation.dto.dataset.DatasetImportResponse
import org.shopflow.backend.presentation.dto.dataset.ImportKaggleDatasetRequest
import org.shopflow.backend.presentation.dto.product.CategoryResponse
import org.shopflow.backend.presentation.dto.product.CreateCategoryRequest
import org.shopflow.backend.presentation.dto.product.CreateProductRequest
import org.shopflow.backend.presentation.dto.product.PageResponse
import org.shopflow.backend.presentation.dto.product.ProductDetailResponse
import org.shopflow.backend.presentation.dto.product.ProductSummaryResponse
import org.shopflow.backend.presentation.dto.product.UpdateProductRequest
import java.util.UUID

private val CANONICAL_UUID_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun resolveCategoryIdOrSlug(idOrSlug: String): Pair<String?, String?> {
    val trimmed = idOrSlug.trim()
    if (trimmed.isEmpty()) {
        throw DomainValidationException("Category identifier cannot be blank.")
    }
    return if (CANONICAL_UUID_REGEX.matches(trimmed)) {
        UUID.fromString(trimmed).toString() to null
    } else {
        null to trimmed
    }
}

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

fun resolveExpectedProductVersion(
    ifMatchHeader: String?,
    bodyVersion: Long? = null,
    queryVersion: Long? = null,
): Long {
    val parsedIfMatch =
        ifMatchHeader?.let { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "*" || trimmed.contains(",")) {
                throw DomainValidationException("Invalid If-Match header '$raw'.")
            }
            val withoutWeak =
                if (trimmed.startsWith("W/", ignoreCase = true)) {
                    trimmed.substring(2).trim()
                } else {
                    trimmed
                }
            val unwrapped =
                if (withoutWeak.length >= 2 && withoutWeak.startsWith("\"") && withoutWeak.endsWith("\"")) {
                    withoutWeak.substring(1, withoutWeak.length - 1).trim()
                } else {
                    withoutWeak
                }
            unwrapped.toLongOrNull()
                ?: throw DomainValidationException("Invalid If-Match header '$raw'.")
        }

    val provided = listOfNotNull(parsedIfMatch, bodyVersion, queryVersion)
    if (provided.isEmpty()) {
        throw DomainValidationException("Expected product version is required via 'version' or 'If-Match' header.")
    }
    if (provided.any { it < 0 }) {
        throw DomainValidationException("Expected product version cannot be negative.")
    }
    val distinct = provided.distinct()
    if (distinct.size > 1) {
        throw DomainValidationException("Conflicting expected version values provided.")
    }
    return distinct.single()
}

fun UpdateProductRequest.toCommand(
    productId: String,
    expectedVersion: Long,
): UpdateProductCommand =
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
        expectedVersion = expectedVersion,
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
        version = version,
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
        version = version,
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
