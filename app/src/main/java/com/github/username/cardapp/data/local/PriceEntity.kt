package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prices")
data class PriceEntity(
    @PrimaryKey val cardName: String,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
