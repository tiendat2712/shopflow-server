package org.shopflow.backend.infrastructure.storage.cloudinary

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.shopflow.backend.domain.exception.ImageStorageException
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.shopflow.backend.domain.service.storage.UploadedImageResult
import org.springframework.stereotype.Component

@Component
class CloudinaryImageStorageAdapter(
    private val properties: CloudinaryProperties,
) : ImageStoragePort {
    private val client: Cloudinary? by lazy {
        if (!properties.isConfigured()) {
            null
        } else {
            Cloudinary(
                ObjectUtils.asMap(
                    "cloud_name",
                    properties.cloudName.trim(),
                    "api_key",
                    properties.apiKey.trim(),
                    "api_secret",
                    properties.apiSecret.trim(),
                    "secure",
                    properties.secure,
                ),
            )
        }
    }

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun uploadImage(
        fileBytes: ByteArray,
        fileName: String,
        folder: String,
        publicIdHint: String?,
    ): UploadedImageResult {
        val cloudinary =
            client
                ?: throw ImageStorageException(
                    "Cloudinary storage is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.",
                )

        val fullFolder = buildFolderPath(folder)
        val options =
            mutableMapOf<String, Any>(
                "folder" to fullFolder,
                "resource_type" to "image",
                "overwrite" to true,
            )
        if (!publicIdHint.isNullOrBlank()) {
            options["public_id"] = publicIdHint.trim()
            options["unique_filename"] = false
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            val response = cloudinary.uploader().upload(fileBytes, options) as Map<String, Any?>
            val secureUrl =
                (response["secure_url"] ?: response["url"])?.toString()
                    ?: throw ImageStorageException("Cloudinary upload response did not return an image URL.")
            val publicId =
                response["public_id"]?.toString()
                    ?: throw ImageStorageException("Cloudinary upload response did not return a public_id.")
            val format = response["format"]?.toString() ?: "png"
            val bytes = (response["bytes"] as? Number)?.toLong() ?: fileBytes.size.toLong()

            UploadedImageResult(
                url = secureUrl,
                publicId = publicId,
                format = format,
                bytes = bytes,
            )
        } catch (ex: ImageStorageException) {
            throw ex
        } catch (ex: Exception) {
            throw ImageStorageException("Failed to upload image '$fileName' to Cloudinary: ${ex.message}", ex)
        }
    }

    override fun deleteImage(publicId: String) {
        val cloudinary =
            client
                ?: throw ImageStorageException(
                    "Cloudinary storage is not configured. Set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET.",
                )

        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
        } catch (ex: Exception) {
            throw ImageStorageException("Failed to delete image '$publicId' from Cloudinary: ${ex.message}", ex)
        }
    }

    private fun buildFolderPath(subFolder: String): String {
        val root = properties.rootFolder.trim().trim('/')
        val cleanSub = subFolder.trim().trim('/')
        return if (root.isEmpty()) cleanSub else "$root/$cleanSub"
    }
}
