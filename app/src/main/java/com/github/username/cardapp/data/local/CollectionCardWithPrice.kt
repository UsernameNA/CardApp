package com.github.username.cardapp.data.local

import androidx.room.Embedded

data class CollectionCardWithPrice(
    @Embedded val card: CardEntity,
    val quantity: Int,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
