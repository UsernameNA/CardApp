package com.github.username.cardapp.data.local

import androidx.room.Embedded

data class CardWithPrice(
    @Embedded val card: CardEntity,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
