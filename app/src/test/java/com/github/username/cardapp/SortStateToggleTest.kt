package com.github.username.cardapp

import com.github.username.cardapp.data.SortDir
import com.github.username.cardapp.data.SortField
import com.github.username.cardapp.data.SortState
import com.github.username.cardapp.data.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortStateToggleTest {

    @Test
    fun toggleOffToAsc() {
        val state = SortState()
        val toggled = state.toggle(SortField.Cost)
        assertEquals(SortDir.Asc, toggled.cost)
        assertTrue(toggled.priority.contains(SortField.Cost))
    }

    @Test
    fun toggleAscToDesc() {
        val state = SortState(cost = SortDir.Asc, priority = listOf(SortField.Name, SortField.Cost))
        val toggled = state.toggle(SortField.Cost)
        assertEquals(SortDir.Desc, toggled.cost)
    }

    @Test
    fun toggleDescToOff() {
        val state = SortState(cost = SortDir.Desc, priority = listOf(SortField.Name, SortField.Cost))
        val toggled = state.toggle(SortField.Cost)
        assertEquals(SortDir.Off, toggled.cost)
        assertTrue(!toggled.priority.contains(SortField.Cost))
    }

    @Test
    fun toggleMovesToEndOfPriority() {
        val state = SortState(
            name = SortDir.Asc,
            cost = SortDir.Asc,
            priority = listOf(SortField.Cost, SortField.Name),
        )
        val toggled = state.toggle(SortField.Cost)
        assertEquals(listOf(SortField.Name, SortField.Cost), toggled.priority)
    }

    @Test
    fun dirReadsEachField() {
        val state = SortState(name = SortDir.Off, cost = SortDir.Asc, rarity = SortDir.Desc, price = SortDir.Asc)
        assertEquals(SortDir.Off, state.dir(SortField.Name))
        assertEquals(SortDir.Asc, state.dir(SortField.Cost))
        assertEquals(SortDir.Desc, state.dir(SortField.Rarity))
        assertEquals(SortDir.Asc, state.dir(SortField.Price))
    }

    @Test
    fun keysMatchPersistedSortPriorityValues() {
        // Saved sort priorities hold these strings; renaming one would drop users' saved order.
        assertEquals(listOf("name", "cost", "rarity", "price"), SortField.entries.map { it.key })
        for (field in SortField.entries) assertEquals(field, SortField.fromKey(field.key))
    }

    @Test
    fun unknownKeyIsIgnored() {
        assertNull(SortField.fromKey("unknown"))
        assertNull(SortField.fromKey("Name"))
    }
}
