package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "collection",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["name"],
            childColumns = ["cardName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CollectionEntryEntity(
    @PrimaryKey val cardName: String,
    val quantity: Int,
)
