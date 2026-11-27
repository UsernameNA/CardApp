package com.github.username.cardapp

import com.github.username.cardapp.data.CollectionImportParser
import com.github.username.cardapp.data.local.CollectionEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionImportParserTest {

    private val validNames = listOf("Ruby Core", "Wild Boar", "Arthurian Legends Promo Card", "Maelström", "Philosopher's Stone", "X Marks the Spot")

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

    @Test
    fun leadingByteOrderMarkIsIgnored() {
        val result = CollectionImportParser.parse("\uFEFF3 Ruby Core\n1 Wild Boar", validNames)
        assertEquals(listOf("Ruby Core" to 3, "Wild Boar" to 1), result.entries.map { it.cardName to it.quantity })
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun acceptsTabsMultipleSpacesAndAnXAfterTheQuantity() {
        for (line in listOf("3 Ruby Core", "3\tRuby Core", "3   Ruby Core", "3x Ruby Core", "3X Ruby Core", "3 x Ruby Core", "3 X\tRuby Core")) {
            val result = CollectionImportParser.parse(line, validNames)
            assertEquals(line, listOf(CollectionEntryEntity(cardName = "Ruby Core", quantity = 3)), result.entries)
            assertTrue(line, result.unmatched.isEmpty())
        }
    }

    @Test
    fun cardNamesStartingWithXAreNotMistakenForTheMarker() {
        val result = CollectionImportParser.parse("2 X Marks the Spot\n1x X Marks the Spot", validNames)
        assertEquals(listOf(CollectionEntryEntity(cardName = "X Marks the Spot", quantity = 3)), result.entries)
    }

    @Test
    fun namesMatchIgnoringDiacriticsAndQuoteStyle() {
        val result = CollectionImportParser.parse("1 Maelstrom\n2 MAELSTRÖM\n1 Philosopher’s Stone\n1 ruby  core", validNames)
        assertEquals(
            listOf("Maelström" to 3, "Philosopher's Stone" to 1, "Ruby Core" to 1),
            result.entries.map { it.cardName to it.quantity },
        )
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun hugeQuantitiesAreCappedInsteadOfOverflowing() {
        val max = CollectionEntryEntity.MAX_QUANTITY
        val result = CollectionImportParser.parse("999999999 Ruby Core\n999999999 Ruby Core\n50000 Wild Boar", validNames)
        assertEquals(listOf("Ruby Core" to max, "Wild Boar" to max), result.entries.map { it.cardName to it.quantity })
    }

    @Test
    fun quantitiesTooLongToBeANumberAreUnmatched() {
        val result = CollectionImportParser.parse("99999999999999999999 Ruby Core", validNames)
        assertTrue(result.entries.isEmpty())
        assertEquals(listOf("99999999999999999999 Ruby Core"), result.unmatched)
    }
}
