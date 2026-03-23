package com.github.username.cardapp

import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import com.github.username.cardapp.ui.common.applyFilter
import com.github.username.cardapp.ui.common.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFilterTest {

    private fun card(
        name: String,
        cardType: String = "Minion",
        rarity: String = "Ordinary",
        cost: Int = 3,
        elements: String = "Fire",
        subTypes: String = "",
        rulesText: String = "",
        fireThreshold: Int = 0,
        waterThreshold: Int = 0,
        earthThreshold: Int = 0,
        airThreshold: Int = 0,
    ) = CardEntity(
        name = name,
        primarySlug = "slug-$name",
        elements = elements,
        subTypes = subTypes,
        cardType = cardType,
        rarity = rarity,
        cost = cost,
        attack = 0,
        defence = 0,
        life = null,
        rulesText = rulesText,
        airThreshold = airThreshold,
        earthThreshold = earthThreshold,
        fireThreshold = fireThreshold,
        waterThreshold = waterThreshold,
    )

    private val cards = listOf(
        card("Ember Drake", cardType = "Minion", rarity = "Exceptional", cost = 3, fireThreshold = 2),
        card("Tide Caller", cardType = "Spell", rarity = "Ordinary", cost = 2, waterThreshold = 1),
        card("Iron Sentinel", cardType = "Minion", rarity = "Unique", cost = 5, earthThreshold = 3),
        card("Gale Sprite", cardType = "Minion", rarity = "Elite", cost = 1, airThreshold = 1),
        card("Lava Pit", cardType = "Site", rarity = "Ordinary", cost = 0),
    )

    // --- Query filter ---

    @Test
    fun noFilterReturnsAllSortedByName() {
        val result = cards.applyFilter(CardFilterState())
        assertEquals(5, result.size)
        assertEquals("Ember Drake", result[0].name)
        assertEquals("Gale Sprite", result[1].name)
    }

    @Test
    fun queryFiltersByName() {
        val result = cards.applyFilter(CardFilterState(query = "ember"))
        assertEquals(1, result.size)
        assertEquals("Ember Drake", result[0].name)
    }

    @Test
    fun queryFiltersByRulesText() {
        val cardsWithRules = listOf(
            card("Wizard", rulesText = "Spellcaster\nGenesis → Draw a spell."),
            card("Knight", rulesText = "Charge"),
        )
        val result = cardsWithRules.applyFilter(CardFilterState(query = "genesis"))
        assertEquals(1, result.size)
        assertEquals("Wizard", result[0].name)
    }

    @Test
    fun queryIsCaseInsensitive() {
        val result = cards.applyFilter(CardFilterState(query = "TIDE"))
        assertEquals(1, result.size)
    }

    // --- Type filter ---

    @Test
    fun filterByType() {
        val result = cards.applyFilter(CardFilterState(types = setOf("Minion")))
        assertEquals(3, result.size)
        assertTrue(result.all { it.cardType == "Minion" })
    }

    @Test
    fun filterByMultipleTypes() {
        val result = cards.applyFilter(CardFilterState(types = setOf("Minion", "Site")))
        assertEquals(4, result.size)
    }

    // --- Rarity filter ---

    @Test
    fun filterByRarity() {
        val result = cards.applyFilter(CardFilterState(rarities = setOf("Ordinary")))
        assertEquals(2, result.size)
    }

    // --- Element filter ---

    @Test
    fun filterByElementAny() {
        val result = cards.applyFilter(CardFilterState(elements = setOf("Fire")))
        assertEquals(1, result.size)
        assertEquals("Ember Drake", result[0].name)
    }

    @Test
    fun filterByElementNone() {
        val result = cards.applyFilter(CardFilterState(elements = setOf("None")))
        assertEquals(1, result.size)
        assertEquals("Lava Pit", result[0].name)  // site with no thresholds
    }

    @Test
    fun filterByMultipleElementsMatchAny() {
        val result = cards.applyFilter(
            CardFilterState(elements = setOf("Fire", "Water"), elementMatchAll = false),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun filterByMultipleElementsMatchAll() {
        // No card has both fire AND water
        val result = cards.applyFilter(
            CardFilterState(elements = setOf("Fire", "Water"), elementMatchAll = true),
        )
        assertEquals(0, result.size)
    }

    // --- Combined filters ---

    @Test
    fun combinedQueryAndType() {
        val result = cards.applyFilter(
            CardFilterState(query = "e", types = setOf("Minion")),
        )
        // "Ember Drake", "Iron Sentinel", "Gale Sprite" are minions, all contain 'e'
        assertEquals(3, result.size)
    }

    // --- Sort ---

    @Test
    fun sortByCostAsc() {
        val sort = SortState(name = SortDir.Off, cost = SortDir.Asc, priority = listOf("cost"))
        val result = cards.applyFilter(CardFilterState(sort = sort))
        assertEquals(0, result[0].cost)   // Lava Pit
        assertEquals(1, result[1].cost)   // Gale Sprite
        assertEquals(5, result.last().cost)
    }

    @Test
    fun sortByCostDesc() {
        val sort = SortState(name = SortDir.Off, cost = SortDir.Desc, priority = listOf("cost"))
        val result = cards.applyFilter(CardFilterState(sort = sort))
        assertEquals(5, result[0].cost)
        assertEquals(0, result.last().cost)
    }

    @Test
    fun sortByRarityAsc() {
        val sort = SortState(name = SortDir.Off, rarity = SortDir.Asc, priority = listOf("rarity"))
        val result = cards.applyFilter(CardFilterState(sort = sort))
        assertEquals("Ordinary", result[0].rarity)
        assertEquals("Unique", result.last().rarity)
    }

    @Test
    fun sortByPriceAsc() {
        val prices = mapOf(
            "Ember Drake" to PriceInfo(5.0, 4.0, "Alpha"),
            "Tide Caller" to PriceInfo(1.0, 0.8, "Alpha"),
            "Iron Sentinel" to PriceInfo(20.0, 18.0, "Alpha"),
        )
        val sort = SortState(name = SortDir.Off, price = SortDir.Asc, priority = listOf("price"))
        val result = cards.applyFilter(CardFilterState(sort = sort), prices)
        assertEquals("Tide Caller", result[0].name)
        assertEquals("Iron Sentinel", result[2].name)
        // Cards without prices sort to end
    }
}

class SortStateToggleTest {

    @Test
    fun toggleOffToAsc() {
        val state = SortState()
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Asc, toggled.cost)
        assertTrue(toggled.priority.contains("cost"))
    }

    @Test
    fun toggleAscToDesc() {
        val state = SortState(cost = SortDir.Asc, priority = listOf("name", "cost"))
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Desc, toggled.cost)
    }

    @Test
    fun toggleDescToOff() {
        val state = SortState(cost = SortDir.Desc, priority = listOf("name", "cost"))
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Off, toggled.cost)
        assertTrue(!toggled.priority.contains("cost"))
    }

    @Test
    fun toggleMovesToEndOfPriority() {
        val state = SortState(
            name = SortDir.Asc,
            cost = SortDir.Asc,
            priority = listOf("cost", "name"),
        )
        // Toggle cost again (Asc -> Desc) — should move to end of priority
        val toggled = state.toggle("cost")
        assertEquals(listOf("name", "cost"), toggled.priority)
    }

    @Test
    fun toggleUnknownFieldReturnsUnchanged() {
        val state = SortState()
        val toggled = state.toggle("unknown")
        assertEquals(state, toggled)
    }
}
