package com.github.username.cardapp

import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import com.github.username.cardapp.ui.common.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
