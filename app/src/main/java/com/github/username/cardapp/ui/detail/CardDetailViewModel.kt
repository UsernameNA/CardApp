package com.github.username.cardapp.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.username.cardapp.CardDetail
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.FaqEntry
import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val card: StateFlow<CardEntity?> = repository.getCardByName(cardName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val variants: StateFlow<List<CardVariantEntity>> = repository.getVariantsByCardName(cardName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prices: StateFlow<Map<String, PriceInfo>> = repository.prices

    private val _faqs = MutableStateFlow<List<FaqEntry>>(emptyList())
    val faqs: StateFlow<List<FaqEntry>> = _faqs.asStateFlow()

    init {
        viewModelScope.launch { _faqs.value = repository.getFaqs(cardName) }
    }

    fun addToCollection() {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }
}
