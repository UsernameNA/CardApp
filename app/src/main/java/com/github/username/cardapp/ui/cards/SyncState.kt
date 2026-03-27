package com.github.username.cardapp.ui.cards

sealed interface SyncState {
    data object Idle : SyncState
    data object SyncingCards : SyncState
    data object Complete : SyncState
    data class Error(val message: String) : SyncState
}
