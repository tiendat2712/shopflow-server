package org.shopflow.backend.application.query.product

data class ListAdminCategoriesQuery(
    val page: Int = 0,
    val size: Int = 20,
    val isActive: Boolean? = null,
    val parentId: String? = null,
    val keyword: String? = null,
    val sortBy: String = "displayOrder",
    val sortDir: String = "asc",
    val sortField: CategorySortField = CategorySortField.DISPLAY_ORDER,
    val sortDirection: SortDirection = SortDirection.ASC,
)
