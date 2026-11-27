package com.github.username.cardapp.ui.collection

import com.github.username.cardapp.data.local.CollectionEntryEntity

/** Writes a collection as "<quantity> <card name>" lines, the format the importer reads. */
object CollectionExportFormat {
    fun format(entries: List<CollectionEntryEntity>): String =
        entries
            .sortedBy { it.cardName }
            .joinToString("\n") { "${it.quantity} ${it.cardName}" }
}
