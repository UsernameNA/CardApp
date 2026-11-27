package com.github.username.cardapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val name: String,
    // Display image: the first print (earliest set, standard finish first) that has a bundled
    // image, or a pinned override; see CatalogueBuilder.
    val primarySlug: String,
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
    @ColumnInfo(defaultValue = "") val setNames: String = "",
    /** [com.github.username.cardapp.data.TextFold.searchKey] of name, type, subtypes and rules text. */
    @ColumnInfo(defaultValue = "") val searchKey: String = "",
    /** Folded name, so "Älvalinne" sorts among the A's rather than after Z. */
    @ColumnInfo(defaultValue = "") val sortKey: String = "",
)
