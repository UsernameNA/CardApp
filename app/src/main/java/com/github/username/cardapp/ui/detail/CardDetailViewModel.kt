package com.github.username.cardapp.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.username.cardapp.CardDetail
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.FaqEntry
import com.github.username.cardapp.data.ImportState
import com.github.username.cardapp.data.local.CardVariantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CardRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CardDetail>()
    val cardName: String = route.cardName

    val state: StateFlow<CardDetailState> =
        combine(repository.getCardByName(cardName), repository.importState) { card, importState ->
            when {
                card != null -> CardDetailState.Loaded(card)
                // The catalogue may still be importing; only a settled catalogue can lack the card.
                importState is ImportState.Complete || importState is ImportState.Error -> CardDetailState.NotFound
                else -> CardDetailState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardDetailState.Loading)

    val variants: StateFlow<List<CardVariantEntity>> = repository.getVariantsByCardName(cardName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val marketPrice: StateFlow<Double?> = repository.marketPrice(cardName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ownedCount: StateFlow<Int> = repository.filteredCollection(CardFilterState())
        .map { entries -> entries.firstOrNull { it.card.name == cardName }?.quantity ?: 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _faqs = MutableStateFlow<List<FaqEntry>>(emptyList())
    val faqs: StateFlow<List<FaqEntry>> = _faqs.asStateFlow()

    init {
        repository.ensureDataLoaded()
        viewModelScope.launch { _faqs.value = repository.getFaqs(cardName) }
    }

    fun addToCollection() {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }
}
