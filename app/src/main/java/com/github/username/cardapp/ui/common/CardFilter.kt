package com.github.username.cardapp.ui.common

import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.local.CardEntity

private val RARITY_ORDER = mapOf(
    "ordinary" to 0,
    "exceptional" to 1,
    "elite" to 2,
    "unique" to 3,
)

fun List<CardEntity>.applyFilter(
    state: CardFilterState,
    prices: Map<String, PriceInfo> = emptyMap(),
): List<CardEntity> {
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
            "price" -> when (s.price) {
                SortDir.Asc -> compareBy<CardEntity> { prices[it.name]?.marketPrice ?: Double.MAX_VALUE }
                SortDir.Desc -> compareByDescending<CardEntity> { prices[it.name]?.marketPrice ?: -1.0 }
                SortDir.Off -> null
            }
            else -> null
        }
    }

    if (comparators.isNotEmpty()) {
        val combined = comparators.reduce { acc, c -> acc.then(c) }
        val final = if (s.name == SortDir.Off) combined.thenBy { it.name.lowercase() } else combined
        result = result.sortedWith(final)
    } else {
        result = result.sortedBy { it.name.lowercase() }
    }

    return result
}
