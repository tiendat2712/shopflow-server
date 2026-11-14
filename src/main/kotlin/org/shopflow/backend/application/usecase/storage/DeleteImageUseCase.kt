package org.shopflow.backend.application.usecase.storage

import org.shopflow.backend.application.command.storage.DeleteImageCommand
import org.shopflow.backend.domain.exception.InvalidMediaFileException
import org.shopflow.backend.domain.service.storage.ImageStoragePort
import org.springframework.stereotype.Service

@Service
class DeleteImageUseCase(
    private val imageStoragePort: ImageStoragePort,
) {
    fun execute(command: DeleteImageCommand) {
        val trimmedPublicId = command.publicId.trim()
        if (trimmedPublicId.isBlank()) {
            throw InvalidMediaFileException("Image publicId cannot be blank.")
        }
        imageStoragePort.deleteImage(trimmedPublicId)
    }
}
