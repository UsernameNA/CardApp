package com.github.username.cardapp

import androidx.sqlite.db.SupportSQLiteProgram
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.FilterQueryBuilder
import com.github.username.cardapp.data.SortDir
import com.github.username.cardapp.data.SortField
import com.github.username.cardapp.data.SortState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryBuilderTest {

    private fun buildSql(state: CardFilterState, collection: Boolean = false): String {
        val query = FilterQueryBuilder.build(state, collection)
        return query.sql
    }

    private fun boundArgs(state: CardFilterState): List<Any?> {
        val query = FilterQueryBuilder.build(state)
        val args = arrayOfNulls<Any?>(query.argCount)
        query.bindTo(object : SupportSQLiteProgram {
            override fun bindNull(index: Int) { args[index - 1] = null }
            override fun bindLong(index: Int, value: Long) { args[index - 1] = value }
            override fun bindDouble(index: Int, value: Double) { args[index - 1] = value }
            override fun bindString(index: Int, value: String) { args[index - 1] = value }
            override fun bindBlob(index: Int, value: ByteArray) { args[index - 1] = value }
            override fun clearBindings() {}
            override fun close() {}
        })
        return args.toList()
    }

    // --- Base query ---

    @Test
    fun defaultFilterReturnsAllCardsSortedByName() {
        val sql = buildSql(CardFilterState())
        assertTrue(sql.contains("SELECT cards.*"))
        assertTrue(sql.contains("LEFT JOIN prices"))
        assertTrue(sql.contains("ORDER BY cards.sortKey ASC"))
    }

    @Test
    fun collectionModeScopesToOwnedCards() {
        val sql = buildSql(CardFilterState(), collection = true)
        assertTrue(sql.contains("INNER JOIN collection"))
        assertTrue(sql.contains("collection.quantity"))
    }

    // --- Text search ---

    @Test
    fun textSearchUsesTheFoldedSearchKey() {
        val sql = buildSql(CardFilterState(query = "dragon"))
        assertTrue(sql.contains("cards.searchKey LIKE ?"))
        assertFalse(sql.contains("cards.name LIKE"))
    }

    @Test
    fun textSearchFoldsTheQueryLikeTheStoredKey() {
        assertEquals("%maelstrom%", boundArgs(CardFilterState(query = "MAELSTRÖM"))[0])
        assertEquals("%alvalinne%", boundArgs(CardFilterState(query = "Älvalinne"))[0])
        assertEquals("%can't%", boundArgs(CardFilterState(query = "Can’t"))[0])
    }

    @Test
    fun textSearchEscapesWildcardsAfterFolding() {
        assertEquals("""%a\_\%%""", boundArgs(CardFilterState(query = "Ä_%"))[0])
    }

    @Test
    fun textSearchEscapesLikeWildcards() {
        val state = CardFilterState(query = "50%_off")
        val sql = buildSql(state)
        assertTrue(sql.contains("cards.searchKey LIKE ? ESCAPE '\\'"))
        assertEquals("%50\\%\\_off%", boundArgs(state)[0])
    }

    @Test
    fun textSearchEscapesBackslash() {
        val state = CardFilterState(query = """a\b""")
        assertEquals("""%a\\b%""", boundArgs(state)[0])
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

    @Test
    fun setFilterEscapesLikeWildcards() {
        val state = CardFilterState(sets = setOf("Set_100%"))
        val sql = buildSql(state)
        assertTrue(sql.contains("cards.setNames LIKE ? ESCAPE '\\'"))
        assertEquals("%,Set\\_100\\%,%", boundArgs(state)[0])
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
        val sort = SortState(name = SortDir.Off, cost = SortDir.Asc, priority = listOf(SortField.Cost))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("ORDER BY cards.cost ASC"))
    }

    @Test
    fun sortByRarityDesc() {
        val sort = SortState(name = SortDir.Off, rarity = SortDir.Desc, priority = listOf(SortField.Rarity))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("CASE cards.rarity"))
        assertTrue(sql.contains("DESC"))
    }

    @Test
    fun sortByPriceAsc() {
        val sort = SortState(name = SortDir.Off, price = SortDir.Asc, priority = listOf(SortField.Price))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("prices.marketPrice"))
        assertTrue(sql.contains("ASC"))
    }

    @Test
    fun multiFieldSort() {
        val sort = SortState(cost = SortDir.Asc, name = SortDir.Desc, priority = listOf(SortField.Cost, SortField.Name))
        val sql = buildSql(CardFilterState(sort = sort))
        val costIdx = sql.indexOf("cards.cost ASC")
        val nameIdx = sql.indexOf("cards.sortKey DESC")
        assertTrue(costIdx < nameIdx)
    }
}
