package com.github.username.cardapp

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.scan.ScannedCards
import com.github.username.cardapp.ui.scan.ScannedEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ScannedCardsTest {

    private fun card(name: String) = CardEntity(
        name = name,
        primarySlug = name.lowercase(),
        elements = "",
        subTypes = "",
        cardType = "Minion",
        rarity = "Ordinary",
        cost = 0,
        attack = 0,
        defence = 0,
        life = null,
        rulesText = "",
        airThreshold = 0,
        earthThreshold = 0,
        fireThreshold = 0,
        waterThreshold = 0,
    )

    private fun entries(vararg pairs: Pair<String, Int>) =
        pairs.map { (name, count) -> ScannedEntry(card(name), count) }

    // ── incrementExisting ─────────────────────────────────────────────────────

    @Test
    fun incrementRaisesCountInPlaceWithoutReordering() {
        val list = entries("A" to 1, "B" to 2, "C" to 1)

        val result = ScannedCards.incrementExisting(list, "B")

        assertEquals(listOf("A", "B", "C"), result.map { it.card.name })
        assertEquals(3, result[1].count)
    }

    @Test
    fun incrementIsNoOpForMissingCard() {
        val list = entries("A" to 1)
        assertEquals(list, ScannedCards.incrementExisting(list, "Z"))
    }

    // ── removeOne ───────────────────────────────────────────────────────────────

    @Test
    fun removeOneDecrementsWhenCountAboveOne() {
        val list = entries("A" to 1, "B" to 3)

        val result = ScannedCards.removeOne(list, "B")

        assertEquals(listOf("A", "B"), result.map { it.card.name })
        assertEquals(2, result[1].count)
    }

    @Test
    fun removeOneDropsEntryWhenCountReachesZero() {
        val list = entries("A" to 1, "B" to 1)

        val result = ScannedCards.removeOne(list, "B")

        assertEquals(listOf("A"), result.map { it.card.name })
    }

    @Test
    fun removeOneIsNoOpForMissingCard() {
        val list = entries("A" to 1)
        assertEquals(list, ScannedCards.removeOne(list, "Z"))
    }

    // ── addOrBumpToFront ─────────────────────────────────────────────────────────

    @Test
    fun addNewCardGoesToFrontWithCountOne() {
        val list = entries("A" to 1, "B" to 1)

        val result = ScannedCards.addOrBumpToFront(list, card("C"))

        assertEquals(listOf("C", "A", "B"), result.map { it.card.name })
        assertEquals(1, result[0].count)
    }

    @Test
    fun addExistingCardMovesToFrontAndIncrements() {
        val list = entries("A" to 1, "B" to 2, "C" to 1)

        val result = ScannedCards.addOrBumpToFront(list, card("B"))

        assertEquals(listOf("B", "A", "C"), result.map { it.card.name })
        assertEquals(3, result[0].count)
    }
}
