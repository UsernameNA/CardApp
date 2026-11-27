package com.github.username.cardapp

import com.github.username.cardapp.data.AssetTextReader
import com.github.username.cardapp.data.BundledDataVersion
import com.github.username.cardapp.data.CardRepositoryImpl
import com.github.username.cardapp.data.ImportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import gate decides whether an install rebuilds its database copy of the bundled
 * data on launch. Getting it wrong is invisible on a fresh install (which always imports)
 * but leaves upgraders on stale data, so it is pinned down here.
 */
class CardRepositoryImportTest {

    private val cardsJson = """[{"name":"Ruby Core","sets":[{"name":"Alpha","releasedAt":"2023-01-01",
        "variants":[{"slug":"001-ruby_core-b-s","finish":"Standard"}]}]}]"""
    private val pricesJson = """{"cards":[
        {"productName":"Ruby Core","setName":"Alpha","marketPrice":2.5,"lowestPrice":2.0},
        {"productName":"Ruby Core (Foil)","setName":"Alpha","marketPrice":9.0}
    ]}"""
    private val images = setOf("001-ruby_core-b-s")
    private val currentVersion = BundledDataVersion.of(cardsJson, pricesJson, images)

    private fun assets(cards: String = cardsJson, prices: String = pricesJson) = AssetTextReader { name ->
        when (name) {
            "cards.json" -> cards
            "prices.json" -> prices
            else -> throw IllegalStateException("no asset: $name")
        }
    }

    private fun repository(dao: FakeCardDao, assets: AssetTextReader = assets()) = CardRepositoryImpl(
        assets = assets,
        images = { images },
        dao = dao,
        json = Json { ignoreUnknownKeys = true },
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private fun CardRepositoryImpl.awaitFinalState(): ImportState = runBlocking {
        ensureDataLoaded()
        withTimeout(5_000) { importState.first { it is ImportState.Complete || it is ImportState.Error } }
    }

    @Test
    fun emptyDatabaseImportsBundledData() {
        val dao = FakeCardDao()

        assertEquals(ImportState.Complete, repository(dao).awaitFinalState())
        assertEquals(listOf("Ruby Core"), dao.importedCards?.map { it.name })
        assertEquals(currentVersion, dao.storedDataVersion)
    }

    @Test
    fun importKeepsOnlyPricesForCatalogueCards() {
        val dao = FakeCardDao()

        repository(dao).awaitFinalState()

        assertEquals(listOf("Ruby Core" to 2.5), dao.importedPrices?.map { it.cardName to it.marketPrice })
    }

    @Test
    fun unchangedBundledDataIsNotReimported() {
        val dao = FakeCardDao(cardCount = 1, dataVersion = currentVersion)

        assertEquals(ImportState.Complete, repository(dao).awaitFinalState())
        assertEquals(0, dao.importCount)
    }

    @Test
    fun changedBundledDataIsReimported() {
        // An app update that ships a new catalogue must reach existing installs.
        val dao = FakeCardDao(cardCount = 1, dataVersion = "from-the-previous-release")

        assertEquals(ImportState.Complete, repository(dao).awaitFinalState())
        assertEquals(1, dao.importCount)
        assertEquals(currentVersion, dao.storedDataVersion)
    }

    @Test
    fun installWithoutARecordedVersionIsReimported() {
        // Databases from before the version was recorded (v1.5.0 and older).
        val dao = FakeCardDao(cardCount = 1100, dataVersion = null)

        repository(dao).awaitFinalState()

        assertEquals(1, dao.importCount)
    }

    @Test
    fun emptyCatalogueIsRejectedRatherThanImported() {
        val dao = FakeCardDao(cardCount = 1100, dataVersion = "old")

        val state = repository(dao, assets(cards = "[]")).awaitFinalState()

        assertTrue("expected Error but was $state", state is ImportState.Error)
        assertEquals(0, dao.importCount)
    }

    @Test
    fun brokenPriceListStillImportsTheCatalogue() {
        val dao = FakeCardDao()

        val state = repository(dao, assets(prices = "not json")).awaitFinalState()

        assertEquals(ImportState.Complete, state)
        assertEquals(1, dao.importCount)
        assertEquals(emptyList<Any>(), dao.importedPrices)
    }

    @Test
    fun failedImportCanBeRetried() {
        val dao = FakeCardDao()
        var failNext = true
        val flaky = AssetTextReader { name ->
            if (failNext) {
                failNext = false
                throw IllegalStateException("asset read failed")
            }
            assets().read(name)
        }
        val repo = repository(dao, flaky)

        assertTrue(repo.awaitFinalState() is ImportState.Error)
        assertNull(dao.importedCards)

        assertEquals(ImportState.Complete, repo.awaitFinalState())
        assertEquals(1, dao.importCount)
    }
}
