package org.shopflow.backend.application.usecase.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.shopflow.backend.application.command.storage.DeleteImageCommand
import org.shopflow.backend.application.command.storage.UploadImageCommand
import org.shopflow.backend.domain.exception.InvalidMediaFileException
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.shopflow.backend.domain.service.storage.UploadedImageResult

class UploadImageUseCaseTest {
    private val fakeStorage = FakeImageStoragePort()
    private val uploadImageUseCase = UploadImageUseCase(fakeStorage)
    private val deleteImageUseCase = DeleteImageUseCase(fakeStorage)

    @Test
    fun `uploads valid PNG JPEG and WEBP images to whitelisted folders`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01)
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00)
        val webpBytes = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.US_ASCII)

        val pngResult =
            uploadImageUseCase.execute(
                UploadImageCommand(pngBytes, "banner.png", "image/png", pngBytes.size.toLong(), "categories"),
            )
        val jpegResult =
            uploadImageUseCase.execute(
                UploadImageCommand(jpegBytes, "shoe.jpeg", "image/jpeg", jpegBytes.size.toLong(), "PRODUCTS"),
            )
        val webpResult =
            uploadImageUseCase.execute(
                UploadImageCommand(webpBytes, "avatar.webp", "image/webp", webpBytes.size.toLong(), "avatars"),
            )

        assertEquals("shopflow/categories/banner.png", pngResult.publicId)
        assertEquals("shopflow/products/shoe.jpeg", jpegResult.publicId)
        assertEquals("shopflow/avatars/avatar.webp", webpResult.publicId)

        deleteImageUseCase.execute(DeleteImageCommand("  ${pngResult.publicId}  "))
        assertEquals(listOf("shopflow/categories/banner.png"), fakeStorage.deletedPublicIds)
    }

    @Test
    fun `rejects empty files, oversized files, invalid folders, and spoofed magic bytes`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

        assertThrows(InvalidMediaFileException::class.java) {
            uploadImageUseCase.execute(UploadImageCommand(ByteArray(0), "empty.png", "image/png", 0L, "products"))
        }
        assertThrows(InvalidMediaFileException::class.java) {
            val oversized = UploadImageUseCase.MAX_IMAGE_SIZE_BYTES + 1
            uploadImageUseCase.execute(UploadImageCommand(pngBytes, "big.png", "image/png", oversized, "products"))
        }
        assertThrows(InvalidMediaFileException::class.java) {
            val textPayload = "plain-text-not-an-image".toByteArray()
            uploadImageUseCase.execute(
                UploadImageCommand(textPayload, "evil.png", "image/png", textPayload.size.toLong(), "products"),
            )
        }
        assertThrows(InvalidMediaFileException::class.java) {
            uploadImageUseCase.execute(
                UploadImageCommand(pngBytes, "mismatch.jpg", "image/jpeg", pngBytes.size.toLong(), "products"),
            )
        }
        assertThrows(InvalidMediaFileException::class.java) {
            uploadImageUseCase.execute(
                UploadImageCommand(pngBytes, "ok.png", "image/png", pngBytes.size.toLong(), "../secrets"),
            )
        }
        assertThrows(InvalidMediaFileException::class.java) {
            deleteImageUseCase.execute(DeleteImageCommand("   "))
        }
    }

    private class FakeImageStoragePort : ImageStoragePort {
        val deletedPublicIds = mutableListOf<String>()

        override fun uploadImage(
            fileBytes: ByteArray,
            fileName: String,
            folder: String,
            publicIdHint: String?,
        ): UploadedImageResult {
            val publicId = "shopflow/$folder/$fileName"
            return UploadedImageResult(
                url = "https://res.cloudinary.com/demo/image/upload/$publicId",
                publicId = publicId,
                format = fileName.substringAfterLast('.', "png"),
                bytes = fileBytes.size.toLong(),
            )
        }

        override fun deleteImage(publicId: String) {
            deletedPublicIds += publicId
        }
    }
}
