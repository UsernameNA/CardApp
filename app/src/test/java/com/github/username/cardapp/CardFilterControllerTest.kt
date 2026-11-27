package com.github.username.cardapp

import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.SortDir
import com.github.username.cardapp.data.SortField
import com.github.username.cardapp.data.SortState
import com.github.username.cardapp.data.toggle
import com.github.username.cardapp.ui.common.CardFilterController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

class CardFilterControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queries = CopyOnWriteArrayList<CardFilterState>()
    private val savedSorts = CopyOnWriteArrayList<SortState>()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun controller(
        debounceMillis: Long = 100,
        loadSavedSort: suspend () -> SortState = { SortState() },
    ): CardFilterController<String> {
        val controller = CardFilterController(
            scope = scope,
            loadSavedSort = loadSavedSort,
            saveSort = { savedSorts += it },
            sets = flowOf(emptyList()),
            query = { filter -> queries += filter; flowOf(listOf(filter.query)) },
            typingDebounceMillis = debounceMillis,
        )
        // Keep the results flow subscribed, as a screen would.
        scope.launch { controller.results.collect { } }
        return controller
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(3_000) { while (!condition()) delay(5) }
    }

    @Test
    fun firstQueryWaitsForSavedSort() = runBlocking {
        val saved = SortState(name = SortDir.Off, cost = SortDir.Desc, priority = listOf(SortField.Cost))
        controller(loadSavedSort = { delay(50); saved })

        awaitUntil { queries.isNotEmpty() }
        delay(100)
        assertEquals(listOf(saved), queries.map { it.sort })
    }

    @Test
    fun unreadableSavedSortFallsBackToDefault() = runBlocking {
        controller(loadSavedSort = { throw IOException("corrupt") })

        awaitUntil { queries.isNotEmpty() }
        assertEquals(SortState(), queries.single().sort)
    }

    @Test
    fun sortChangesAreSaved() = runBlocking {
        val controller = controller()
        awaitUntil { queries.isNotEmpty() }

        controller.updateFilter { it.copy(sort = it.sort.toggle(SortField.Price)) }

        awaitUntil { savedSorts.isNotEmpty() }
        assertEquals(SortDir.Asc, savedSorts.single().price)
    }

    @Test
    fun filterChangesWithoutSortChangeAreNotSaved() = runBlocking {
        val controller = controller()
        awaitUntil { queries.isNotEmpty() }

        controller.updateFilter { it.copy(types = setOf("Minion")) }

        awaitUntil { queries.size == 2 }
        assertEquals(emptyList<SortState>(), savedSorts)
    }

    @Test
    fun typingQueriesOnlyOnceItPauses() = runBlocking {
        val controller = controller()
        awaitUntil { queries.isNotEmpty() }

        controller.updateQuery("r")
        controller.updateQuery("ru")
        controller.updateQuery("rub")

        awaitUntil { controller.results.value == listOf("rub") }
        delay(150)
        assertEquals(listOf("", "rub"), queries.map { it.query })
    }

    @Test
    fun otherChangesApplyWithoutWaitingForTyping() = runBlocking {
        val controller = controller(debounceMillis = 60_000)
        awaitUntil { queries.isNotEmpty() }

        controller.updateQuery("ru")
        controller.updateFilter { it.copy(types = setOf("Minion")) }

        awaitUntil { queries.size == 2 }
        assertEquals("ru", queries.last().query)
        assertEquals(setOf("Minion"), queries.last().types)
    }

    @Test
    fun queryAndFilterChangedTogetherApplyAtOnce() = runBlocking {
        val controller = controller(debounceMillis = 60_000)
        awaitUntil { queries.isNotEmpty() }

        // What the results flow sees when a keystroke and a filter tap are conflated.
        controller.updateFilter { it.copy(query = "ru", types = setOf("Minion")) }

        awaitUntil { queries.size == 2 }
        assertEquals("ru", queries.last().query)
    }

    @Test
    fun clearingTheQueryAppliesAtOnce() = runBlocking {
        val controller = controller(debounceMillis = 60_000)
        awaitUntil { queries.isNotEmpty() }
        controller.updateFilter { it.copy(types = setOf("Site")) }
        awaitUntil { queries.size == 2 }

        controller.updateQuery("ru")
        controller.clearFilters()

        awaitUntil { queries.size == 3 }
        assertEquals(CardFilterState(), queries.last())
        assertFalse(queries.any { it.query == "ru" })
    }

    @Test
    fun clearFiltersKeepsTheSort() = runBlocking {
        val sorted = SortState(name = SortDir.Desc)
        val controller = controller(loadSavedSort = { sorted })
        awaitUntil { queries.isNotEmpty() }
        controller.updateFilter { it.copy(rarities = setOf("Unique"), query = "dragon") }

        controller.clearFilters()

        assertEquals(CardFilterState(sort = sorted), controller.filterState.value)
    }

    @Test
    fun expandingTheFilterPanelDoesNotRequery() = runBlocking {
        val controller = controller()
        awaitUntil { queries.isNotEmpty() }

        controller.updateFilter { it.copy(filtersExpanded = true) }
        delay(200)

        assertEquals(1, queries.size)
        assertEquals(true, controller.filterState.value.filtersExpanded)
    }
}
