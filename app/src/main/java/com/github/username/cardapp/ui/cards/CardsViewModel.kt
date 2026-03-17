package com.github.username.cardapp.ui.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.applyFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState
    data object SyncingCards : SyncState
    data object Complete : SyncState
    data class Error(val message: String) : SyncState
}

class CardsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository =
        CardRepository(
            context = app,
            db = AppDatabase.getInstance(app),
        )

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

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.needsCardSync()) sync()
            repository.loadPrices()
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
        _filterState.value = transform(_filterState.value)
    }
}
