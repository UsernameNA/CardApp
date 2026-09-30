package com.github.username.cardapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.username.cardapp.data.FilterQueryBuilder
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.MetadataEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The DAO and the SQL [FilterQueryBuilder] generates, run against real SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val ruby = testCard("Ruby Core", cardType = "Artifact", rarity = "Ordinary", cost = 1, setNames = ",Alpha,")
    private val serpent = testCard("Sea Serpent", rarity = "Elite", cost = 5, waterThreshold = 2, setNames = ",Beta,")
    private val drake = testCard("Ember Drake", rarity = "Exceptional", cost = 3, fireThreshold = 1, setNames = ",Alpha,Beta,")

    private suspend fun importCatalogue(
        cards: List<CardEntity>,
        prices: List<PriceEntity> = emptyList(),
        version: String = "v1",
    ) = dao.replaceBundledData(emptyList(), cards, emptyList(), prices, version)

    private suspend fun collection() = dao.getCollectionEntries().first().map { it.card.name to it.quantity }

    private suspend fun names(filter: CardFilterState) =
        dao.filteredCards(FilterQueryBuilder.build(filter)).first().map { it.card.name }

    @Test
    fun reimportKeepsTheCollectionAndHidesOrphansUntilTheirCardReturns() = runBlocking {
        importCatalogue(listOf(ruby, serpent))
        dao.incrementCollectionEntry("Ruby Core")
        dao.incrementCollectionEntry("Ruby Core")
        dao.incrementCollectionEntry("Sea Serpent")

        importCatalogue(listOf(ruby), version = "v2")
        assertEquals(listOf("Ruby Core" to 2), collection())
        assertEquals(1, dao.countOrphanedCollectionEntries())

        importCatalogue(listOf(ruby, serpent), version = "v3")
        assertEquals(listOf("Ruby Core" to 2, "Sea Serpent" to 1), collection())
        assertEquals("v3", dao.getMetadata(MetadataEntity.BUNDLED_DATA_VERSION))
    }

    @Test
    fun reimportReplacesPricesInsteadOfAccumulatingThem() = runBlocking {
        importCatalogue(listOf(ruby), prices = listOf(PriceEntity("Ruby Core", 1.0, 1.0)))
        importCatalogue(listOf(ruby), prices = emptyList(), version = "v2")

        assertEquals(emptyList<PriceEntity>(), dao.getAllPrices().first())
    }

    @Test
    fun removeOneDecrementsThenDeletes() = runBlocking {
        importCatalogue(listOf(ruby))
        dao.upsertCollectionEntries(listOf(com.github.username.cardapp.data.local.CollectionEntryEntity("Ruby Core", 2)))

        dao.removeOneFromCollection("Ruby Core")
        assertEquals(listOf("Ruby Core" to 1), collection())

        dao.removeOneFromCollection("Ruby Core")
        assertEquals(emptyList<Pair<String, Int>>(), collection())
    }

    @Test
    fun textSearchMatchesNameTypeAndRulesLiterally() = runBlocking {
        importCatalogue(listOf(ruby, serpent, drake.copy(rulesText = "Deals 100% damage")))

        assertEquals(listOf("Sea Serpent"), names(CardFilterState(query = "serp")))
        assertEquals(listOf("Ruby Core"), names(CardFilterState(query = "artifact")))
        assertEquals("% is literal, not a wildcard", listOf("Ember Drake"), names(CardFilterState(query = "100%")))
    }

    @Test
    fun setRarityAndElementFiltersCombine() = runBlocking {
        importCatalogue(listOf(ruby, serpent, drake))

        assertEquals(listOf("Ember Drake", "Sea Serpent"), names(CardFilterState(sets = setOf("Beta"))))
        assertEquals(listOf("Sea Serpent"), names(CardFilterState(rarities = setOf("Elite"))))
        assertEquals(listOf("Ruby Core"), names(CardFilterState(elements = setOf("None"))))
        assertEquals(
            listOf("Ember Drake", "Sea Serpent"),
            names(CardFilterState(elements = setOf("Fire", "Water"))),
        )
        assertEquals(
            "match-all needs both thresholds",
            emptyList<String>(),
            names(CardFilterState(elements = setOf("Fire", "Water"), elementMatchAll = true)),
        )
    }

    @Test
    fun priceSortPutsUnpricedCardsLast() = runBlocking {
        importCatalogue(
            listOf(ruby, serpent, drake),
            prices = listOf(PriceEntity("Ruby Core", 1.0, 1.0), PriceEntity("Sea Serpent", 9.0, 8.0)),
        )
        val byPriceDesc = SortState(name = SortDir.Off, price = SortDir.Desc, priority = listOf("price"))

        assertEquals(listOf("Sea Serpent", "Ruby Core", "Ember Drake"), names(CardFilterState(sort = byPriceDesc)))
    }

    @Test
    fun collectionQueryJoinsQuantityAndPrice() = runBlocking {
        importCatalogue(listOf(ruby, serpent), prices = listOf(PriceEntity("Sea Serpent", 9.0, 8.0)))
        dao.incrementCollectionEntry("Sea Serpent")

        val rows = dao.filteredCollectionCards(FilterQueryBuilder.build(CardFilterState(), collection = true)).first()

        assertEquals(listOf(Triple("Sea Serpent", 1, 9.0)), rows.map { Triple(it.card.name, it.quantity, it.marketPrice) })
    }
}
