package com.github.username.cardapp.ui.common

import com.github.username.cardapp.data.local.CardEntity

enum class SortDir { Off, Asc, Desc }

data class SortState(
    val name: SortDir = SortDir.Asc,
    val cost: SortDir = SortDir.Off,
    val rarity: SortDir = SortDir.Off,
    // Order in which sorts were last activated — last = primary.
    // Stores field names: "name", "cost", "rarity"
    val priority: List<String> = listOf("name"),
)

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

private val RARITY_ORDER = mapOf(
    "ordinary" to 0,
    "exceptional" to 1,
    "elite" to 2,
    "unique" to 3,
)

/** Cycle a sort field: Off → Asc → Desc → Off, and update priority. */
fun SortState.toggle(field: String): SortState {
    val current = when (field) {
        "name" -> name
        "cost" -> cost
        "rarity" -> rarity
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
        else -> this
    }
}

fun List<CardEntity>.applyFilter(state: CardFilterState): List<CardEntity> {
    var result = this

    if (state.query.isNotBlank()) {
        val q = state.query.lowercase()
        result = result.filter { card ->
            card.name.lowercase().contains(q) ||
                card.cardType.lowercase().contains(q) ||
                card.subTypes.lowercase().contains(q) ||
                card.rulesText.lowercase().contains(q)
        }
    }

    if (state.types.isNotEmpty()) {
        val normalizedTypes = state.types.mapTo(mutableSetOf()) { it.lowercase() }
        result = result.filter { it.cardType.lowercase() in normalizedTypes }
    }

    if (state.rarities.isNotEmpty()) {
        val normalizedRarities = state.rarities.mapTo(mutableSetOf()) { it.lowercase() }
        result = result.filter { it.rarity.lowercase() in normalizedRarities }
    }

    if (state.elements.isNotEmpty()) {
        val wantNone = "None" in state.elements
        val elementKeys = state.elements - "None"
        result = result.filter { card ->
            val cardElements = buildSet {
                if (card.fireThreshold > 0) add("Fire")
                if (card.waterThreshold > 0) add("Water")
                if (card.earthThreshold > 0) add("Earth")
                if (card.airThreshold > 0) add("Air")
            }
            val hasNone = cardElements.isEmpty()
            when {
                elementKeys.isEmpty() && wantNone -> hasNone
                wantNone && !state.elementMatchAll -> hasNone || elementKeys.any { it in cardElements }
                wantNone && state.elementMatchAll -> hasNone || elementKeys.all { it in cardElements }
                state.elementMatchAll -> elementKeys.all { it in cardElements }
                else -> elementKeys.any { it in cardElements }
            }
        }
    }

    // Build a chained comparator from active sorts, primary (most recent) first.
    val s = state.sort
    val comparators = s.priority.asReversed().mapNotNull { field ->
        when (field) {
            "name" -> when (s.name) {
                SortDir.Asc -> compareBy<CardEntity> { it.name.lowercase() }
                SortDir.Desc -> compareByDescending<CardEntity> { it.name.lowercase() }
                SortDir.Off -> null
            }
            "cost" -> when (s.cost) {
                SortDir.Asc -> compareBy<CardEntity> { it.cost }
                SortDir.Desc -> compareByDescending<CardEntity> { it.cost }
                SortDir.Off -> null
            }
            "rarity" -> when (s.rarity) {
                SortDir.Asc -> compareBy<CardEntity> { RARITY_ORDER[it.rarity.lowercase()] ?: 0 }
                SortDir.Desc -> compareByDescending<CardEntity> { RARITY_ORDER[it.rarity.lowercase()] ?: 0 }
                SortDir.Off -> null
            }
            else -> null
        }
    }

    if (comparators.isNotEmpty()) {
        val combined = comparators.reduce { acc, c -> acc.then(c) }
        // Always tie-break on name if name isn't already a sort key
        val final = if (s.name == SortDir.Off) combined.thenBy { it.name.lowercase() } else combined
        result = result.sortedWith(final)
    } else {
        // No sort active — default to name asc
        result = result.sortedBy { it.name.lowercase() }
    }

    return result
}
