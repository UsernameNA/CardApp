package com.github.username.cardapp.ui.common

data class CardFilterState(
    val query: String = "",
    val sets: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val elements: Set<String> = emptySet(),
    val elementMatchAll: Boolean = false,
    val sort: SortState = SortState(),
    val filtersExpanded: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || sets.isNotEmpty() || types.isNotEmpty() || rarities.isNotEmpty() || elements.isNotEmpty()
}
