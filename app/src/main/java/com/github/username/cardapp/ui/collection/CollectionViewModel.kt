package com.github.username.cardapp.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.applyFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CardRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {

    val allEntries: StateFlow<List<CollectionCardRow>> = repository.collection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    val entries: StateFlow<List<CollectionCardRow>> = combine(allEntries, _filterState, repository.prices) { entries, filter, priceMap ->
        val quantityByName = entries.associate { it.card.name to it.quantity }
        val sortedCards = entries.map { it.card }.applyFilter(filter, priceMap)
        sortedCards.map { card -> CollectionCardRow(card, quantityByName[card.name] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalUniqueCount: StateFlow<Int> = allEntries
        .combine(_filterState) { entries, _ -> entries.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val prices: StateFlow<Map<String, PriceInfo>> = repository.prices

    val totalCardCount: StateFlow<Int> = allEntries
        .combine(_filterState) { entries, _ -> entries.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
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
            val entries = text.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val spaceIndex = line.indexOf(' ')
                    if (spaceIndex < 1) return@mapNotNull null
                    val qty = line.substring(0, spaceIndex).toIntOrNull() ?: return@mapNotNull null
                    val name = line.substring(spaceIndex + 1).trim()
                    if (name.isEmpty() || qty <= 0) return@mapNotNull null
                    CollectionEntryEntity(cardName = name, quantity = qty)
                }
            if (entries.isNotEmpty()) repository.addToCollection(entries)
        }
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
