package org.shopflow.backend.application.usecase.storage

import org.shopflow.backend.application.command.storage.UploadImageCommand
import org.shopflow.backend.application.dto.storage.UploadedImageDto
import org.shopflow.backend.domain.exception.InvalidMediaFileException
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.springframework.stereotype.Service

@Service
class UploadImageUseCase(
    private val imageStoragePort: ImageStoragePort,
) {
    fun execute(command: UploadImageCommand): UploadedImageDto {
        val folder = normalizeAndValidateFolder(command.folder)
        if (command.sizeBytes <= 0L || command.fileBytes.isEmpty()) {
            throw InvalidMediaFileException("Uploaded image file cannot be empty.")
        }
        if (command.sizeBytes > MAX_IMAGE_SIZE_BYTES || command.fileBytes.size > MAX_IMAGE_SIZE_BYTES) {
            throw InvalidMediaFileException("Image size must not exceed 5 MB.")
        }

        val detectedFormat =
            detectFormatByMagicBytes(command.fileBytes)
                ?: throw InvalidMediaFileException("Only JPG, PNG, and WEBP image files are supported.")

        validateExtensionConsistency(command.originalFileName, detectedFormat)
        validateContentTypeConsistency(command.contentType, detectedFormat)

        val safeFileName = command.originalFileName.trim().ifBlank { "upload.$detectedFormat" }
        val result =
            imageStoragePort.uploadImage(
                fileBytes = command.fileBytes,
                fileName = safeFileName,
                folder = folder,
            )

        return UploadedImageDto(
            url = result.url,
            publicId = result.publicId,
        )
    }

    private fun normalizeAndValidateFolder(rawFolder: String): String {
        val normalized = rawFolder.trim().lowercase()
        if (normalized !in ALLOWED_FOLDERS) {
            throw InvalidMediaFileException(
                "Invalid target folder '$rawFolder'. Allowed folders: ${ALLOWED_FOLDERS.sorted().joinToString()}.",
            )
        }
        return normalized
    }

    private fun detectFormatByMagicBytes(bytes: ByteArray): String? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "jpg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "webp"
        }
        return null
    }

    private fun validateExtensionConsistency(
        originalFileName: String,
        detectedFormat: String,
    ) {
        val extension = originalFileName.substringAfterLast('.', "").trim().lowercase()
        if (extension.isEmpty()) {
            return
        }
        val normalizedExt = if (extension == "jpeg") "jpg" else extension
        if (normalizedExt != detectedFormat) {
            throw InvalidMediaFileException("File extension '.$extension' does not match detected image content '$detectedFormat'.")
        }
    }

    private fun validateContentTypeConsistency(
        contentType: String?,
        detectedFormat: String,
    ) {
        val normalized =
            contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?: return
        if (normalized.isBlank() || normalized == "application/octet-stream") {
            return
        }
        val allowedMimeTypes =
            when (detectedFormat) {
                "jpg" -> setOf("image/jpeg", "image/jpg")
                "png" -> setOf("image/png")
                "webp" -> setOf("image/webp")
                else -> emptySet()
            }
        if (normalized !in allowedMimeTypes) {
            throw InvalidMediaFileException("Content-Type '$normalized' does not match detected image content '$detectedFormat'.")
        }
    }

    companion object {
        const val MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024
        val ALLOWED_FOLDERS = setOf("avatars", "products", "categories")
    }
}
