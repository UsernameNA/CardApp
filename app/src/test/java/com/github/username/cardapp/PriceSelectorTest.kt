package com.github.username.cardapp

import com.github.username.cardapp.data.PriceEntryJson
import com.github.username.cardapp.data.PriceSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceSelectorTest {

    private val catalogue = setOf("Ruby Core")

    private fun entry(name: String, set: String, market: Double) =
        PriceEntryJson(productName = name, setName = set, marketPrice = market, lowestPrice = market)

    private fun marketPrice(vararg entries: PriceEntryJson): Double? =
        PriceSelector.select(entries.toList(), catalogue).singleOrNull { it.cardName == "Ruby Core" }?.marketPrice

    @Test
    fun nonPromoPriceBeatsEarlierCheaperPromoPrice() {
        assertEquals(5.0, marketPrice(entry("Ruby Core", "Arthurian Legends Promo", 1.0), entry("Ruby Core", "Alpha", 5.0)))
    }

    @Test
    fun promoNeverReplacesNonPromoEvenIfCheaper() {
        assertEquals(5.0, marketPrice(entry("Ruby Core", "Alpha", 5.0), entry("Ruby Core", "Dust Reward Promos", 1.0)))
    }

    @Test
    fun lowestNonPromoPriceWins() {
        assertEquals(
            3.0,
            marketPrice(entry("Ruby Core", "Alpha", 5.0), entry("Ruby Core", "Beta", 3.0), entry("Ruby Core", "Gamma", 4.0)),
        )
    }

    @Test
    fun promoPriceUsedWhenOnlyPromoPrintingExists() {
        assertEquals(2.0, marketPrice(entry("Ruby Core", "Arthurian Legends Promo", 2.0)))
    }

    @Test
    fun welcomeKitPromoNeverReplacesNonPromo() {
        assertEquals(5.0, marketPrice(entry("Ruby Core", "Alpha", 5.0), entry("Ruby Core", "Welcome Kit Promos", 1.0)))
    }

    @Test
    fun missingMarketPriceFallsBackToMedianThenLowest() {
        assertEquals(4.0, marketPrice(PriceEntryJson("Ruby Core", "Alpha", marketPrice = null, medianPrice = 4.0, lowestPrice = 3.0)))
        assertEquals(3.0, marketPrice(PriceEntryJson("Ruby Core", "Alpha", marketPrice = null, lowestPrice = 3.0)))
    }

    @Test
    fun realMarketPriceBeatsCheaperEstimate() {
        assertEquals(
            5.0,
            marketPrice(
                PriceEntryJson("Ruby Core", "Frost Mage vs Lavamancer", marketPrice = null, medianPrice = 1.0),
                entry("Ruby Core", "Alpha", 5.0),
            ),
        )
    }

    @Test
    fun entryWithNoPriceAtAllIsSkipped() {
        assertEquals(null, marketPrice(PriceEntryJson("Ruby Core", "Alpha")))
    }

    @Test
    fun priceNameWithoutDiacriticsMatchesAccentedCard() {
        val prices = PriceSelector.select(
            listOf(entry("Maelstrom", "Beta", 2.0), entry("Alvalinne Dryads", "Alpha", 1.0)),
            setOf("Maelström", "Älvalinne Dryads"),
        )

        assertEquals(setOf("Maelström" to 2.0, "Älvalinne Dryads" to 1.0), prices.map { it.cardName to it.marketPrice }.toSet())
    }

    @Test
    fun productsThatAreNotCatalogueCardsAreDropped() {
        val prices = PriceSelector.select(
            listOf(entry("Ruby Core", "Alpha", 5.0), entry("Ruby Core (Foil)", "Alpha", 20.0)),
            catalogue,
        )

        assertEquals(listOf("Ruby Core"), prices.map { it.cardName })
    }
}
