package com.github.username.cardapp.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.applyFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CardRepository(
        context = app,
        db = AppDatabase.getInstance(app),
    )

    private val allEntries: StateFlow<List<CollectionCardRow>> = repository.collection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    val entries: StateFlow<List<CollectionCardRow>> = combine(allEntries, _filterState) { entries, filter ->
        val quantityByName = entries.associate { it.card.name to it.quantity }
        val sortedCards = entries.map { it.card }.applyFilter(filter)
        sortedCards.map { card -> CollectionCardRow(card, quantityByName[card.name] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalUniqueCount: StateFlow<Int> = allEntries
        .combine(_filterState) { entries, _ -> entries.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val totalCardCount: StateFlow<Int> = allEntries
        .combine(_filterState) { entries, _ -> entries.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun increment(cardName: String) {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }

    fun decrement(cardName: String) {
        viewModelScope.launch { repository.removeOneFromCollection(cardName) }
    }

    fun updateFilter(transform: (CardFilterState) -> CardFilterState) {
        _filterState.value = transform(_filterState.value)
    }
}
