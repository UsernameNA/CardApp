package com.github.username.cardapp.ui.detail

import com.github.username.cardapp.data.local.CardEntity

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data object NotFound : CardDetailState
    data class Loaded(val card: CardEntity) : CardDetailState
}
