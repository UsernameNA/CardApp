package com.github.username.cardapp.ui.common

import android.util.Log
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.SortState
import com.github.username.cardapp.data.local.SetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Search/filter/sort state for a card list, shared by the screens that show one. The
 * saved sort is restored before the first query runs and every sort change is saved.
 *
 * @param query runs the list query for a filter.
 */
class CardFilterController<T>(
    private val scope: CoroutineScope,
    loadSavedSort: suspend () -> SortState,
    private val saveSort: suspend (SortState) -> Unit,
    sets: Flow<List<SetEntity>>,
    query: (CardFilterState) -> Flow<List<T>>,
    private val typingDebounceMillis: Long = TYPING_DEBOUNCE_MILLIS,
) {

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    // Hold the first query until the saved sort is applied, so the list isn't fetched
    // (and briefly shown) in default order first.
    private val sortRestored = MutableStateFlow(false)

    // The saved sort is written before sortRestored is set, so the first filter read after
    // it already carries the saved sort.
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<T>> = flow { sortRestored.first { it }; emitAll(_filterState) }
        // Expanding the filter panel doesn't change the results.
        .map { it.copy(filtersExpanded = false) }
        .distinctUntilChanged()
        .debounceTyping()
        .flatMapLatest(query)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val setNames: StateFlow<List<String>> = sets
        .map { list -> list.map { it.name } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        scope.launch {
            try {
                val savedSort = loadSavedSort()
                _filterState.update { it.copy(sort = savedSort) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read saved sort; using the default", e)
            } finally {
                sortRestored.value = true
            }
        }
    }

    fun updateFilter(transform: (CardFilterState) -> CardFilterState) {
        val old = _filterState.value
        val new = transform(old)
        _filterState.value = new
        if (old.sort != new.sort) {
            scope.launch { saveSort(new.sort) }
        }
    }

    fun updateQuery(query: String) = updateFilter { it.copy(query = query) }

    fun clearFilters() = updateFilter { CardFilterState(sort = it.sort) }

    /**
     * Waits for a pause in typing before querying; other filter changes apply at once. A
     * change counts as typing only if the query is all that changed: state updates can be
     * conflated, so a query edit and a filter tap may arrive as one change.
     */
    @OptIn(FlowPreview::class)
    private fun Flow<CardFilterState>.debounceTyping(): Flow<CardFilterState> = flow {
        var previous: CardFilterState? = null
        emitAll(
            debounce { filter ->
                val last = previous
                val typing = last != null && filter.query.isNotEmpty() &&
                    filter.query != last.query && filter.copy(query = last.query) == last
                previous = filter
                if (typing) typingDebounceMillis else 0L
            },
        )
    }

    private companion object {
        const val TAG = "CardFilterController"
        const val TYPING_DEBOUNCE_MILLIS = 200L
    }
}
