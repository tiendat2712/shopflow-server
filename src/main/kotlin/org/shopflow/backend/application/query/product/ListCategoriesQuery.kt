package org.shopflow.backend.application.query.product

data class ListCategoriesQuery(
    val page: Int = 0,
    val size: Int = 20,
    val onlyActive: Boolean = false,
    val parentId: String? = null,
    val keyword: String? = null,
    val sortBy: String = "displayOrder",
    val sortDir: String = "asc",
    val sortField: CategorySortField = CategorySortField.DISPLAY_ORDER,
    val sortDirection: SortDirection = SortDirection.ASC,
)
