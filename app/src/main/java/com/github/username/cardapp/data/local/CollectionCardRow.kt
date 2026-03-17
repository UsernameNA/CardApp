package com.github.username.cardapp.data.local

import androidx.room.Embedded

data class CollectionCardRow(
    @Embedded val card: CardEntity,
    val quantity: Int,
)
