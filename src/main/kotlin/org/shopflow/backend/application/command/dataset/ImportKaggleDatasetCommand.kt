package org.shopflow.backend.application.command.dataset

data class ImportKaggleDatasetCommand(
    val forceDownload: Boolean = false,
    val uploadCategoryPlaceholders: Boolean = true,
    val refreshExistingProductImages: Boolean = true,
    val maxUsers: Int? = null,
    val maxProducts: Int? = null,
)
