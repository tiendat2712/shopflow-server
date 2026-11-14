package org.shopflow.backend.presentation.dto.dataset

import jakarta.validation.constraints.Min

data class ImportKaggleDatasetRequest(
    val forceDownload: Boolean = false,
    val uploadCategoryPlaceholders: Boolean = true,
    val refreshExistingProductImages: Boolean = true,
    @field:Min(value = 0, message = "maxUsers cannot be negative.")
    val maxUsers: Int? = null,
    @field:Min(value = 0, message = "maxProducts cannot be negative.")
    val maxProducts: Int? = null,
)

data class DatasetImportResponse(
    val sourceDirectory: String,
    val downloadedFromRemote: Boolean,
    val cloudinaryLiveUploadUsed: Boolean,
    val usersInserted: Int,
    val usersSkipped: Int,
    val categoriesInserted: Int,
    val categoriesExisting: Int,
    val productsInserted: Int,
    val productsSkipped: Int,
    val productsImageUpdated: Int,
    val ordersInserted: Int,
    val ordersSkipped: Int,
    val orderItemsInserted: Int,
    val orderItemsSkipped: Int,
    val reviewsInserted: Int,
    val reviewsSkipped: Int,
    val productReviewCountsUpdated: Int,
    val userEventsInserted: Int,
    val userEventsSkipped: Int,
    val categoryImageUrls: Map<String, String>,
    val stagedCsvFiles: Map<String, Long>,
    val durationMs: Long,
)
