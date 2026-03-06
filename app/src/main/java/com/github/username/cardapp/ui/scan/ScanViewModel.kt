package com.github.username.cardapp.ui.scan

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

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CardRepository(
        context = app,
        db = AppDatabase.getInstance(app),
    )

    private val allCards: StateFlow<List<CardEntity>> = repository.cards
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _scannedCards = MutableStateFlow<List<CardEntity>>(emptyList())
    val scannedCards: StateFlow<List<CardEntity>> = _scannedCards.asStateFlow()

    fun scanRandom() {
        val pool = allCards.value
        if (pool.isEmpty()) return
        _scannedCards.value += pool.random()
    }
}
