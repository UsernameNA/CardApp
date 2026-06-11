package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CollectionEntryEntity

/**
 * Outcome of parsing a collection import file: entries that matched a known
 * card (with canonical DB names) and the raw lines that could not be imported.
 */
data class CollectionImportResult(
    val entries: List<CollectionEntryEntity>,
    val unmatched: List<String>,
) {
    val importedCount: Int get() = entries.sumOf { it.quantity }
}
