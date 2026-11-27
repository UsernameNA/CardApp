package com.github.username.cardapp

import com.github.username.cardapp.data.CollectionImportParser
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.ui.collection.CollectionExportFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionExportFormatTest {

    @Test
    fun writesOneQuantityAndNamePerLineSortedByName() {
        val text = CollectionExportFormat.format(
            listOf(
                CollectionEntryEntity(cardName = "Ruby Core", quantity = 3),
                CollectionEntryEntity(cardName = "Abundance", quantity = 1),
            ),
        )
        assertEquals("1 Abundance\n3 Ruby Core", text)
    }

    @Test
    fun emptyCollectionIsEmptyText() {
        assertEquals("", CollectionExportFormat.format(emptyList()))
    }

    @Test
    fun exportRoundTripsThroughTheImporter() {
        val entries = listOf(
            CollectionEntryEntity(cardName = "Abundance", quantity = 2),
            CollectionEntryEntity(cardName = "Lancelot's Lance", quantity = 1),
            CollectionEntryEntity(cardName = "Séance", quantity = 4),
            CollectionEntryEntity(cardName = "X Marks the Spot", quantity = 1),
            CollectionEntryEntity(cardName = "7 League Boots", quantity = 9999),
        )

        val result = CollectionImportParser.parse(
            CollectionExportFormat.format(entries),
            entries.map { it.cardName },
        )

        assertEquals(emptyList<String>(), result.unmatched)
        assertEquals(entries.sortedBy { it.cardName }, result.entries)
    }
}
