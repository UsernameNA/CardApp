package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One owned card and how many copies.
 *
 * Deliberately has no foreign key to [CardEntity]: the catalogue is replaced wholesale
 * whenever the bundled data changes, and a cascade would silently delete every entry
 * whose card was renamed or dropped. Entries without a matching card are kept, hidden
 * by the INNER JOINs that read the collection, and reappear if the card comes back.
 */
@Entity(tableName = "collection")
data class CollectionEntryEntity(
    @PrimaryKey val cardName: String,
    val quantity: Int,
)
