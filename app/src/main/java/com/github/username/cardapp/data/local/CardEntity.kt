package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val name: String,
    val primarySlug: String,       // slug of the standard-finish print from the earliest set, used for display image
    val elements: String,
    val subTypes: String,
    val cardType: String,
    val rarity: String,
    val cost: Int,
    val attack: Int,
    val defence: Int,
    val life: Int?,
    val rulesText: String,
    val airThreshold: Int,
    val earthThreshold: Int,
    val fireThreshold: Int,
    val waterThreshold: Int,
)
