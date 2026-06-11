package com.github.username.cardapp.ui.collection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.CollectionImportParser
import com.github.username.cardapp.data.CollectionImportResult
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.common.CardFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CardRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<CollectionCardWithPrice>> = _filterState
        .flatMapLatest { filter -> repository.filteredCollection(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCardCount: StateFlow<Int> = repository.totalCollectionQuantity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sets: StateFlow<List<SetEntity>> = repository.sets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importResult = MutableStateFlow<CollectionImportResult?>(null)
    val importResult: StateFlow<CollectionImportResult?> = _importResult.asStateFlow()

    init {
        repository.ensureDataLoaded()
        viewModelScope.launch {
            val savedSort = sortPreferences.sortState.first()
            _filterState.value = _filterState.value.copy(sort = savedSort)
        }
    }

    fun increment(cardName: String) {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }

    fun decrement(cardName: String) {
        viewModelScope.launch { repository.removeOneFromCollection(cardName) }
    }

    fun importCollection(text: String) {
        viewModelScope.launch {
            try {
                val result = CollectionImportParser.parse(text, repository.getAllCardNames())
                if (result.entries.isNotEmpty()) repository.addToCollection(result.entries)
                _importResult.value = result
            } catch (e: Exception) {
                Log.w("CollectionViewModel", "Collection import failed", e)
                _importResult.value = CollectionImportResult(emptyList(), listOf("Import failed: ${e.message}"))
            }
        }
    }

    fun dismissImportResult() {
        _importResult.value = null
    }

    fun updateFilter(transform: (CardFilterState) -> CardFilterState) {
        val old = _filterState.value
        val new = transform(old)
        _filterState.value = new
        if (old.sort != new.sort) {
            viewModelScope.launch { sortPreferences.save(new.sort) }
        }
    }
}
