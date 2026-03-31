package com.github.username.cardapp.ui.common

data class CardFilterState(
    val query: String = "",
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val elements: Set<String> = emptySet(),
    val elementMatchAll: Boolean = false,
    val sort: SortState = SortState(),
    val filtersExpanded: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || types.isNotEmpty() || rarities.isNotEmpty() || elements.isNotEmpty()
}
