package com.github.username.cardapp.data

data class SortState(
    val name: SortDir = SortDir.Asc,
    val cost: SortDir = SortDir.Off,
    val rarity: SortDir = SortDir.Off,
    val price: SortDir = SortDir.Off,
    val priority: List<SortField> = listOf(SortField.Name),
) {
    fun dir(field: SortField): SortDir = when (field) {
        SortField.Name -> name
        SortField.Cost -> cost
        SortField.Rarity -> rarity
        SortField.Price -> price
    }
}

/** Cycle a sort field: Off -> Asc -> Desc -> Off, and update priority. */
fun SortState.toggle(field: SortField): SortState {
    val next = when (dir(field)) {
        SortDir.Off -> SortDir.Asc
        SortDir.Asc -> SortDir.Desc
        SortDir.Desc -> SortDir.Off
    }
    val newPriority = if (next == SortDir.Off) {
        priority - field
    } else {
        (priority - field) + field
    }
    return when (field) {
        SortField.Name -> copy(name = next, priority = newPriority)
        SortField.Cost -> copy(cost = next, priority = newPriority)
        SortField.Rarity -> copy(rarity = next, priority = newPriority)
        SortField.Price -> copy(price = next, priority = newPriority)
    }
}
