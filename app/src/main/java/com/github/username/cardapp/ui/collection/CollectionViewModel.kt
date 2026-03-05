package com.github.username.cardapp.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SyncState {
    data object Idle : SyncState
    data object SyncingCards : SyncState
    data object Complete : SyncState
    data class Error(val message: String) : SyncState
}

class CollectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository =
        CardRepository(
            context = app,
            db = AppDatabase.getInstance(
                app
            ),
        )

    val cards: StateFlow<List<CardEntity>> = repository.cards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.needsCardSync()) sync()
        }
    }

    fun sync() {
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
}
