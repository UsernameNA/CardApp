package com.github.username.cardapp

import android.app.Application
import android.net.Uri
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.CollectionImportResult
import com.github.username.cardapp.data.FaqEntry
import com.github.username.cardapp.data.ImportState
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.collection.CollectionFileReader
import com.github.username.cardapp.ui.collection.CollectionImporter
import com.github.username.cardapp.ui.collection.ImportFileTooLargeException
import com.github.username.cardapp.ui.collection.ImportOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CollectionImporterTest {

    private val uri = Uri.parse("content://test/collection.txt")
    private val repository = RecordingRepository(cardNames = listOf("Abundance", "Ruby Core"))

    private fun importing(reader: CollectionFileReader): ImportOutcome = runBlocking {
        CollectionImporter(reader, repository).import(uri)
    }

    @Test
    fun matchedCardsAreAddedAndReported() {
        val outcome = importing { "2 Abundance\n1 Nonexistent Card" }

        val expected = listOf(CollectionEntryEntity(cardName = "Abundance", quantity = 2))
        assertEquals(listOf(expected), repository.added)
        assertEquals(ImportOutcome.Imported(CollectionImportResult(expected, listOf("1 Nonexistent Card"))), outcome)
    }

    @Test
    fun nothingIsAddedWhenNoLineMatches() {
        val outcome = importing { "3 Nonexistent Card" }

        assertEquals(emptyList<List<CollectionEntryEntity>>(), repository.added)
        assertTrue(outcome is ImportOutcome.Imported)
    }

    @Test
    fun blankFileIsReportedAsEmpty() {
        assertEquals(ImportOutcome.Failed("The file is empty."), importing { " \n\n" })
    }

    @Test
    fun readFailuresBecomeMessages() {
        assertEquals(
            ImportOutcome.Failed("The file could not be found."),
            importing { throw FileNotFoundException() },
        )
        assertEquals(
            ImportOutcome.Failed("Permission to read the file was denied."),
            importing { throw SecurityException() },
        )
        assertEquals(
            ImportOutcome.Failed("The file could not be read."),
            importing { throw IOException() },
        )
        assertEquals(
            ImportOutcome.Failed("The file is too large to be a collection list."),
            importing { throw ImportFileTooLargeException(1) },
        )
        assertEquals(emptyList<List<CollectionEntryEntity>>(), repository.added)
    }

    @Test
    fun databaseFailureBecomesAMessage() {
        repository.failAdds = true

        val outcome = importing { "1 Ruby Core" }

        assertEquals(ImportOutcome.Failed("The cards could not be added to your collection."), outcome)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() {
        importing { throw CancellationException("screen closed") }
    }

    private class RecordingRepository(private val cardNames: List<String>) : CardRepository {
        val added = mutableListOf<List<CollectionEntryEntity>>()
        var failAdds = false

        override suspend fun getAllCardNames(): List<String> = cardNames

        override suspend fun addToCollection(entries: List<CollectionEntryEntity>) {
            if (failAdds) throw IllegalStateException("database is closed")
            added += entries
        }

        override val importState: StateFlow<ImportState> = MutableStateFlow(ImportState.Complete)
        override fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>> = error("unused")
        override fun filteredCollection(filter: CardFilterState): Flow<List<CollectionCardWithPrice>> = error("unused")
        override val sets: Flow<List<SetEntity>> get() = error("unused")
        override val priceMap: Flow<Map<String, Double>> get() = error("unused")
        override fun marketPrice(cardName: String): Flow<Double?> = error("unused")
        override val totalCardCount: Flow<Int> get() = error("unused")
        override val totalCollectionQuantity: Flow<Int> get() = error("unused")
        override suspend fun incrementInCollection(cardName: String) = error("unused")
        override suspend fun removeOneFromCollection(cardName: String) = error("unused")
        override fun getCardByName(name: String): Flow<CardEntity?> = error("unused")
        override fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>> = error("unused")
        override suspend fun getFaqs(cardName: String): List<FaqEntry> = error("unused")
        override fun ensureDataLoaded() = error("unused")
    }
}
