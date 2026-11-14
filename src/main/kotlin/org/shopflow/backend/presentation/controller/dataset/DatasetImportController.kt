package org.shopflow.backend.presentation.controller.dataset

import jakarta.validation.Valid
import org.shopflow.backend.application.command.dataset.ImportKaggleDatasetCommand
import org.shopflow.backend.application.usecase.dataset.ImportKaggleDatasetUseCase
import org.shopflow.backend.presentation.converter.toCommand
import org.shopflow.backend.presentation.converter.toResponse
import org.shopflow.backend.presentation.dto.dataset.DatasetImportResponse
import org.shopflow.backend.presentation.dto.dataset.ImportKaggleDatasetRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/datasets")
class DatasetImportController(
    private val importKaggleDatasetUseCase: ImportKaggleDatasetUseCase,
) {
    @PostMapping("/import", "/import-kaggle")
    @PreAuthorize("hasRole('ADMIN')")
    fun importKaggleDataset(
        @Valid @RequestBody(required = false) request: ImportKaggleDatasetRequest? = null,
    ): DatasetImportResponse {
        val command = request?.toCommand() ?: ImportKaggleDatasetCommand()
        return importKaggleDatasetUseCase.execute(command).toResponse()
    }
}
