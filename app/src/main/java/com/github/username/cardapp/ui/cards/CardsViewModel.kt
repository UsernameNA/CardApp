package com.github.username.cardapp.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.ImportState
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.ui.common.CardFilterController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CardRepository,
    sortPreferences: SortPreferences,
) : ViewModel() {

    val filter = CardFilterController(
        scope = viewModelScope,
        loadSavedSort = { sortPreferences.sortState.first() },
        saveSort = sortPreferences::save,
        sets = repository.sets,
        query = repository::filteredCards,
    )

    val cards: StateFlow<List<CardWithPrice>> = filter.results

    val totalCardCount: StateFlow<Int> = repository.totalCardCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val collectedQuantities: StateFlow<Map<String, Int>> = repository.filteredCollection(CardFilterState())
        .map { entries -> entries.associate { it.card.name to it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val importState: StateFlow<ImportState> = repository.importState

    init {
        repository.ensureDataLoaded()
    }

    fun retryDataLoad() = repository.ensureDataLoaded()
}
