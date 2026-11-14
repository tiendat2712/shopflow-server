package org.shopflow.backend.presentation.controller.storage

import org.shopflow.backend.application.command.storage.DeleteImageCommand
import org.shopflow.backend.application.command.storage.UploadImageCommand
import org.shopflow.backend.application.usecase.storage.DeleteImageUseCase
import org.shopflow.backend.application.usecase.storage.UploadImageUseCase
import org.shopflow.backend.presentation.dto.storage.UploadedImageResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/media")
class MediaController(
    private val uploadImageUseCase: UploadImageUseCase,
    private val deleteImageUseCase: DeleteImageUseCase,
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("isAuthenticated()")
    fun uploadImage(
        authentication: Authentication,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("folder", defaultValue = "products") folder: String,
    ): UploadedImageResponse {
        val normalizedFolder = folder.trim().lowercase()
        if (normalizedFolder in CATALOG_WRITE_FOLDERS) {
            val canWriteProductMedia = authentication.authorities.any { it.authority == "PRODUCT_WRITE" }
            if (!canWriteProductMedia) {
                throw AccessDeniedException("PRODUCT_WRITE permission is required to upload catalog images.")
            }
        }

        val uploaded =
            uploadImageUseCase.execute(
                UploadImageCommand(
                    fileBytes = file.bytes,
                    originalFileName = file.originalFilename ?: "upload",
                    contentType = file.contentType,
                    sizeBytes = file.size,
                    folder = folder,
                ),
            )

        return UploadedImageResponse(
            url = uploaded.url,
            publicId = uploaded.publicId,
        )
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteImage(
        @RequestParam("publicId") publicId: String,
    ) {
        deleteImageUseCase.execute(DeleteImageCommand(publicId))
    }

    companion object {
        private val CATALOG_WRITE_FOLDERS = setOf("products", "categories")
    }
}
