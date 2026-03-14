package com.github.username.cardapp.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CollectionCardRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CardRepository(
        context = app,
        db = AppDatabase.getInstance(app),
    )

    val entries: StateFlow<List<CollectionCardRow>> = repository.collection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun increment(cardName: String) {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }

    fun decrement(cardName: String) {
        viewModelScope.launch { repository.removeOneFromCollection(cardName) }
    }
}
