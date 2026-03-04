package com.example.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val rarity: String,
    val cardType: String,
    val rulesText: String,
    val cost: Int,
    val attack: Int,
    val defence: Int,
    val elements: String,
    val subTypes: String,
    val setName: String,
    val finish: String,
    val artist: String,
    val flavorText: String,
    val typeText: String,
    val airThreshold: Int,
    val earthThreshold: Int,
    val fireThreshold: Int,
    val waterThreshold: Int,
)
