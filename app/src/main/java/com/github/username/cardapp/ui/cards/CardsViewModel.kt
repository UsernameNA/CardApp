package com.github.username.cardapp.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.applyFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CardRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {

    private val allCards: StateFlow<List<CardEntity>> = repository.cards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    val cards: StateFlow<List<CardEntity>> = combine(allCards, _filterState, repository.prices) { cards, filter, priceMap ->
        cards.applyFilter(filter, priceMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCardCount: StateFlow<Int> = allCards
        .combine(_filterState) { cards, _ -> cards.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val prices: StateFlow<Map<String, PriceInfo>> = repository.prices

    val collectedQuantities: StateFlow<Map<String, Int>> = repository.collection
        .map { entries -> entries.associate { it.card.name to it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        repository.ensureDataLoaded()
        viewModelScope.launch {
            val savedSort = sortPreferences.sortState.first()
            _filterState.value = _filterState.value.copy(sort = savedSort)
        }
    }

    fun sync() {
        if (_syncState.value == SyncState.SyncingCards) return
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.SyncingCards
                repository.syncCards()
                _syncState.value = SyncState.Complete
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
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
