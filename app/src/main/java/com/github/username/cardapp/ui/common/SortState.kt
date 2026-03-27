package com.github.username.cardapp.ui.common

data class SortState(
    val name: SortDir = SortDir.Asc,
    val cost: SortDir = SortDir.Off,
    val rarity: SortDir = SortDir.Off,
    val price: SortDir = SortDir.Off,
    val priority: List<String> = listOf("name"),
)

/** Cycle a sort field: Off -> Asc -> Desc -> Off, and update priority. */
fun SortState.toggle(field: String): SortState {
    val current = when (field) {
        "name" -> name
        "cost" -> cost
        "rarity" -> rarity
        "price" -> price
        else -> return this
    }
    val next = when (current) {
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
        "name" -> copy(name = next, priority = newPriority)
        "cost" -> copy(cost = next, priority = newPriority)
        "rarity" -> copy(rarity = next, priority = newPriority)
        "price" -> copy(price = next, priority = newPriority)
        else -> this
    }
}
