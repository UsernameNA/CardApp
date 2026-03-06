package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "variants",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["name"],
            childColumns = ["cardName"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["name"],
            childColumns = ["setName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardName"), Index("setName")],
)
data class CardVariantEntity(
    @PrimaryKey val slug: String,
    val cardName: String,
    val setName: String,
    val finish: String,
    val product: String,
    val artist: String,
    val flavorText: String,
    val typeText: String,
)
