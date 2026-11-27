package com.github.username.cardapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.FilterQueryBuilder
import com.github.username.cardapp.data.SortDir
import com.github.username.cardapp.data.SortField
import com.github.username.cardapp.data.SortState
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.MetadataEntity
import com.github.username.cardapp.data.local.PriceEntity
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

    private suspend fun collection() =
        dao.filteredCollectionCards(FilterQueryBuilder.build(CardFilterState(), collection = true)).first()
            .map { it.card.name to it.quantity }

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
        dao.upsertCollectionEntries(listOf(CollectionEntryEntity("Ruby Core", 2)))

        dao.removeOneFromCollection("Ruby Core")
        assertEquals(listOf("Ruby Core" to 1), collection())

        dao.removeOneFromCollection("Ruby Core")
        assertEquals(emptyList<Pair<String, Int>>(), collection())
    }

    @Test
    fun textSearchMatchesNameTypeAndRulesLiterally() = runBlocking {
        val damageDrake = testCard("Ember Drake", rarity = "Exceptional", rulesText = "Deals 100% damage")
        importCatalogue(listOf(ruby, serpent, damageDrake))

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
        val byPriceDesc = SortState(name = SortDir.Off, price = SortDir.Desc, priority = listOf(SortField.Price))

        assertEquals(listOf("Sea Serpent", "Ruby Core", "Ember Drake"), names(CardFilterState(sort = byPriceDesc)))
    }

    @Test
    fun collectionQueryJoinsQuantityAndPrice() = runBlocking {
        importCatalogue(listOf(ruby, serpent), prices = listOf(PriceEntity("Sea Serpent", 9.0, 8.0)))
        dao.incrementCollectionEntry("Sea Serpent")

        val rows = dao.filteredCollectionCards(FilterQueryBuilder.build(CardFilterState(), collection = true)).first()

        assertEquals(listOf(Triple("Sea Serpent", 1, 9.0)), rows.map { Triple(it.card.name, it.quantity, it.marketPrice) })
    }

    @Test
    fun textSearchIgnoresDiacriticsCaseAndCurlyQuotes() = runBlocking {
        val maelstrom = testCard("Maelström")
        val dryads = testCard("Älvalinne Dryads")
        val quoted = testCard("Ruby Core", rulesText = "Can’t be targeted")
        importCatalogue(listOf(maelstrom, dryads, quoted, serpent))

        assertEquals(listOf("Maelström"), names(CardFilterState(query = "maelstrom")))
        assertEquals(listOf("Maelström"), names(CardFilterState(query = "MAELSTRÖM")))
        assertEquals(listOf("Älvalinne Dryads"), names(CardFilterState(query = "alvalinne")))
        assertEquals(listOf("Ruby Core"), names(CardFilterState(query = "can't")))
    }

    @Test
    fun nameSortTreatsAccentedLettersAsTheirBaseLetter() = runBlocking {
        val zephyr = testCard("Zephyr")
        val dryads = testCard("Älvalinne Dryads")
        val boar = testCard("Wild Boar")
        importCatalogue(listOf(zephyr, dryads, boar, ruby))

        assertEquals(listOf("Älvalinne Dryads", "Ruby Core", "Wild Boar", "Zephyr"), names(CardFilterState()))
        val desc = SortState(name = SortDir.Desc, priority = listOf(SortField.Name))
        assertEquals(listOf("Zephyr", "Wild Boar", "Ruby Core", "Älvalinne Dryads"), names(CardFilterState(sort = desc)))
    }

    @Test
    fun collectionQuantityCountsOnlyCardsInTheCatalogue() = runBlocking {
        assertEquals(0, dao.observeCollectionQuantity().first())

        importCatalogue(listOf(ruby, serpent))
        dao.upsertCollectionEntries(listOf(CollectionEntryEntity("Ruby Core", 3), CollectionEntryEntity("Sea Serpent", 2)))
        assertEquals(5, dao.observeCollectionQuantity().first())

        importCatalogue(listOf(ruby), version = "v2")
        assertEquals(3, dao.observeCollectionQuantity().first())
    }

    @Test
    fun collectionQuantitiesAreCappedWhenAdding() = runBlocking {
        importCatalogue(listOf(ruby))
        val max = CollectionEntryEntity.MAX_QUANTITY

        dao.upsertCollectionEntries(listOf(CollectionEntryEntity("Ruby Core", max - 1)))
        dao.upsertCollectionEntries(listOf(CollectionEntryEntity("Ruby Core", 5)))
        assertEquals(listOf("Ruby Core" to max), collection())

        dao.incrementCollectionEntry("Ruby Core")
        assertEquals(listOf("Ruby Core" to max), collection())
    }

    @Test
    fun marketPriceObservesOneCard() = runBlocking {
        importCatalogue(
            listOf(ruby, serpent),
            prices = listOf(PriceEntity("Ruby Core", 1.5, 1.0), PriceEntity("Sea Serpent", 9.0, 8.0)),
        )

        assertEquals(1.5, dao.observeMarketPrice("Ruby Core").first())
        assertEquals(null, dao.observeMarketPrice("Ember Drake").first())
    }
}
