package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.PriceEntity
import java.text.Normalizer

/** Picks one display price per card from the bundled TCGPlayer price list. */
object PriceSelector {

    private val promoSets = setOf("dust reward promos", "arthurian legends promo", "welcome kit promos")

    private class Candidate(val entity: PriceEntity, val isPromo: Boolean, val isEstimate: Boolean)

    /**
     * Prefer the cheapest non-promo printing; fall back to promo prices only for cards
     * that exist solely as promos. A printing with no market price (often a new release)
     * uses its median or lowest listing instead, but any real market price wins over that.
     * Entries that don't name a catalogue card (for example "Bedrock (Foil)") are dropped.
     * TCGPlayer drops diacritics ("Maelstrom"), so names are matched without them.
     */
    fun select(entries: List<PriceEntryJson>, cardNames: Set<String>): List<PriceEntity> {
        val namesByKey = cardNames.associateBy(::matchKey)
        val best = mutableMapOf<String, Candidate>()
        for (entry in entries) {
            val name = entry.productName?.let { namesByKey[matchKey(it)] } ?: continue
            val price = entry.marketPrice ?: entry.medianPrice ?: entry.lowestPrice ?: continue
            val candidate = Candidate(
                entity = PriceEntity(cardName = name, marketPrice = price, lowPrice = entry.lowestPrice ?: price),
                isPromo = entry.setName?.lowercase() in promoSets,
                isEstimate = entry.marketPrice == null,
            )
            val existing = best[name]
            if (existing == null || preference.compare(candidate, existing) < 0) best[name] = candidate
        }
        return best.values.map { it.entity }
    }

    private val preference = compareBy<Candidate>({ it.isPromo }, { it.isEstimate }, { it.entity.marketPrice })

    private val combiningMarks = Regex("\\p{M}+")

    private fun matchKey(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(combiningMarks, "")
}
