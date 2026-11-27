package com.github.username.cardapp.data

sealed interface ImportState {
    data object Idle : ImportState
    data object Importing : ImportState
    data object Complete : ImportState
    data class Error(val message: String) : ImportState
}
