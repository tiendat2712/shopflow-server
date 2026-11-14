package org.shopflow.backend.application.command.storage

class UploadImageCommand(
    val fileBytes: ByteArray,
    val originalFileName: String,
    val contentType: String?,
    val sizeBytes: Long,
    val folder: String,
)
