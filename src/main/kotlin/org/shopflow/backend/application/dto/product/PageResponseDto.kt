package org.shopflow.backend.application.dto.product

data class PageResponseDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean = page + 1 < totalPages,
    val hasPrevious: Boolean = page > 0 && totalPages > 0,
)
