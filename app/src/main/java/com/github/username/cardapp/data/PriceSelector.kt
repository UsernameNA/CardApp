package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.PriceEntity

/** Picks one display price per card from the bundled TCGPlayer price list. */
object PriceSelector {

    private val promoSets = setOf("dust reward promos", "arthurian legends promo")

    /**
     * Prefer the cheapest non-promo printing; fall back to promo prices only for cards
     * that exist solely as promos. Entries that don't name a catalogue card (for example
     * "Bedrock (Foil)") are dropped.
     */
    fun select(entries: List<PriceEntryJson>, cardNames: Set<String>): List<PriceEntity> {
        val best = mutableMapOf<String, Pair<PriceEntity, Boolean>>() // entity to isPromo
        for (entry in entries) {
            val name = entry.productName?.takeIf { it in cardNames } ?: continue
            val market = entry.marketPrice ?: continue
            val isPromo = entry.setName?.lowercase() in promoSets
            val (existing, existingIsPromo) = best[name] ?: (null to false)
            val replace = when {
                existing == null -> true
                existingIsPromo && !isPromo -> true
                existingIsPromo == isPromo && market < (existing.marketPrice ?: Double.MAX_VALUE) -> true
                else -> false
            }
            if (replace) {
                best[name] = PriceEntity(
                    cardName = name,
                    marketPrice = market,
                    lowPrice = entry.lowestPrice ?: market,
                ) to isPromo
            }
        }
        return best.values.map { it.first }
    }
}
