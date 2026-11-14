package org.shopflow.backend.domain.service.storage

data class UploadedImageResult(
    val url: String,
    val publicId: String,
    val format: String,
    val bytes: Long,
)

interface ImageStoragePort {
    fun isConfigured(): Boolean = true

    fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        folder: String,
        publicIdHint: String? = null,
    ): UploadedImageResult

    fun deleteImage(publicId: String)
}
