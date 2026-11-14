package org.shopflow.backend.domain.service.dataset

import java.nio.file.Path

data class PreparedDatasetDirectory(
    val directory: Path,
    val downloadedFromRemote: Boolean,
)

interface DatasetSourcePort {
    fun prepareDatasetDirectory(forceDownload: Boolean): PreparedDatasetDirectory

    fun defaultUserPassword(): String
}
