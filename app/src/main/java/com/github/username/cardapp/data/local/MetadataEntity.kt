package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Named values the app keeps alongside the data they describe. */
@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey val name: String,
    val value: String,
) {
    companion object {
        /** Fingerprint of the bundled assets the catalogue and prices were imported from. */
        const val BUNDLED_DATA_VERSION = "bundled_data_version"
    }
}
