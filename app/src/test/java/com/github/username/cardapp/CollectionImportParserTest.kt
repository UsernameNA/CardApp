package com.github.username.cardapp

import com.github.username.cardapp.data.CollectionImportParser
import com.github.username.cardapp.data.local.CollectionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionImportParserTest {

    private val validNames = listOf("Ruby Core", "Wild Boar", "Arthurian Legends Promo Card")

    @Test
    fun parsesValidLinesIntoEntries() {
        val result = CollectionImportParser.parse("3 Ruby Core\n1 Wild Boar", validNames)
        assertEquals(
            listOf(
                CollectionEntryEntity(cardName = "Ruby Core", quantity = 3),
                CollectionEntryEntity(cardName = "Wild Boar", quantity = 1),
            ),
            result.entries,
        )
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun matchesNamesCaseInsensitivelyAndUsesCanonicalName() {
        val result = CollectionImportParser.parse("2 ruby core", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 2)), result.entries)
    }

    @Test
    fun unknownCardNamesAreReportedAsUnmatched() {
        val result = CollectionImportParser.parse("3 Ruby Core\n2 Sapphire Core", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 3)), result.entries)
        assertEquals(listOf("2 Sapphire Core"), result.unmatched)
    }

    @Test
    fun malformedLinesAreReportedAsUnmatched() {
        val result = CollectionImportParser.parse("Ruby Core\n0 Wild Boar\n-1 Wild Boar\n3 ", validNames)
        assertTrue(result.entries.isEmpty())
        assertEquals(listOf("Ruby Core", "0 Wild Boar", "-1 Wild Boar", "3"), result.unmatched)
    }

    @Test
    fun blankLinesAreIgnoredSilently() {
        val result = CollectionImportParser.parse("\n3 Ruby Core\n\n   \n", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 3)), result.entries)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun duplicateNamesAggregateQuantities() {
        val result = CollectionImportParser.parse("3 Ruby Core\n2 ruby core", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 5)), result.entries)
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val result = CollectionImportParser.parse("  3  Ruby Core  ", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 3)), result.entries)
    }
}
