package com.github.username.cardapp

import com.github.username.cardapp.data.FilterQueryBuilder
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryBuilderTest {

    private fun buildSql(state: CardFilterState, collection: Boolean = false): String {
        val query = FilterQueryBuilder.build(state, collection)
        return query.sql
    }

    // --- Base query ---

    @Test
    fun defaultFilterReturnsAllCardsSortedByName() {
        val sql = buildSql(CardFilterState())
        assertTrue(sql.contains("SELECT cards.*"))
        assertTrue(sql.contains("LEFT JOIN prices"))
        assertTrue(sql.contains("ORDER BY cards.name COLLATE NOCASE ASC"))
    }

    @Test
    fun collectionModeScopesToOwnedCards() {
        val sql = buildSql(CardFilterState(), collection = true)
        assertTrue(sql.contains("INNER JOIN collection"))
        assertTrue(sql.contains("collection.quantity"))
    }

    // --- Text search ---

    @Test
    fun textSearchFiltersNameAndRulesText() {
        val sql = buildSql(CardFilterState(query = "dragon"))
        assertTrue(sql.contains("cards.name LIKE ?"))
        assertTrue(sql.contains("cards.rulesText LIKE ?"))
    }

    // --- Set filter ---

    @Test
    fun setFilterUsesLikeWithSentinels() {
        val sql = buildSql(CardFilterState(sets = setOf("Alpha")))
        assertTrue(sql.contains("cards.setNames LIKE ?"))
    }

    @Test
    fun multipleSetFiltersAreOred() {
        val sql = buildSql(CardFilterState(sets = setOf("Alpha", "Beta")))
        val setClauseCount = Regex("cards\\.setNames LIKE \\?").findAll(sql).count()
        assertEquals(2, setClauseCount)
    }

    // --- Type filter ---

    @Test
    fun typeFilterUsesIn() {
        val sql = buildSql(CardFilterState(types = setOf("Minion", "Spell")))
        assertTrue(sql.contains("cards.cardType IN ("))
    }

    // --- Rarity filter ---

    @Test
    fun rarityFilterUsesIn() {
        val sql = buildSql(CardFilterState(rarities = setOf("Elite")))
        assertTrue(sql.contains("cards.rarity IN ("))
    }

    // --- Element filter ---

    @Test
    fun elementFilterMatchAny() {
        val sql = buildSql(CardFilterState(elements = setOf("Fire", "Water")))
        assertTrue(sql.contains("cards.fireThreshold > 0"))
        assertTrue(sql.contains(" OR "))
    }

    @Test
    fun elementFilterMatchAll() {
        val sql = buildSql(CardFilterState(elements = setOf("Fire", "Water"), elementMatchAll = true))
        assertTrue(sql.contains("cards.fireThreshold > 0"))
        assertTrue(sql.contains(" AND "))
    }

    @Test
    fun elementNoneFilter() {
        val sql = buildSql(CardFilterState(elements = setOf("None")))
        assertTrue(sql.contains("cards.airThreshold = 0"))
        assertTrue(sql.contains("cards.earthThreshold = 0"))
        assertTrue(sql.contains("cards.fireThreshold = 0"))
        assertTrue(sql.contains("cards.waterThreshold = 0"))
    }

    // --- Sort ---

    @Test
    fun sortByCostAsc() {
        val sort = SortState(name = SortDir.Off, cost = SortDir.Asc, priority = listOf("cost"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("ORDER BY cards.cost ASC"))
    }

    @Test
    fun sortByRarityDesc() {
        val sort = SortState(name = SortDir.Off, rarity = SortDir.Desc, priority = listOf("rarity"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("CASE cards.rarity"))
        assertTrue(sql.contains("DESC"))
    }

    @Test
    fun sortByPriceAsc() {
        val sort = SortState(name = SortDir.Off, price = SortDir.Asc, priority = listOf("price"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("prices.marketPrice"))
        assertTrue(sql.contains("ASC"))
    }

    @Test
    fun multiFieldSort() {
        val sort = SortState(cost = SortDir.Asc, name = SortDir.Desc, priority = listOf("cost", "name"))
        val sql = buildSql(CardFilterState(sort = sort))
        val costIdx = sql.indexOf("cards.cost ASC")
        val nameIdx = sql.indexOf("cards.name COLLATE NOCASE DESC")
        assertTrue(costIdx < nameIdx)
    }
}
