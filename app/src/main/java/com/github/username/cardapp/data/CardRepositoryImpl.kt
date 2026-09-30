package com.github.username.cardapp.data

import android.util.Log
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.MetadataEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.ui.common.CardFilterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All card data ships inside the APK; the app never touches the network. The database
 * holds an imported copy, rebuilt whenever the bundled data differs from what was last
 * imported (see [BundledDataVersion]) — so an app update carries new cards and prices
 * to existing installs.
 */
@Singleton
class CardRepositoryImpl @Inject constructor(
    private val assets: AssetTextReader,
    private val images: BundledImageIndex,
    private val dao: CardDao,
    private val json: Json,
    private val appScope: CoroutineScope,
) : CardRepository {

    override fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>> =
        dao.filteredCards(FilterQueryBuilder.build(filter, collection = false))

    override fun filteredCollection(filter: CardFilterState): Flow<List<CollectionCardWithPrice>> =
        dao.filteredCollectionCards(FilterQueryBuilder.build(filter, collection = true))

    override val sets: Flow<List<SetEntity>> = dao.getAllSets()

    override val priceMap: Flow<Map<String, Double>> = dao.getAllPrices()
        .map { list -> list.mapNotNull { p -> p.marketPrice?.let { p.cardName to it } }.toMap() }

    override val totalCardCount: Flow<Int> = dao.observeCardCount()

    override val totalCollectionQuantity: Flow<Int> = dao.getCollectionEntries()
        .map { entries -> entries.sumOf { it.quantity } }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val loadLock = Any()
    private var loadJob: Job? = null

    override fun ensureDataLoaded() {
        synchronized(loadLock) {
            val job = loadJob
            if (job != null && (job.isActive || _syncState.value !is SyncState.Error)) return
            _syncState.value = SyncState.Idle
            loadJob = appScope.launch { loadBundledData() }
        }
    }

    private suspend fun loadBundledData() {
        try {
            val cardsRaw = assets.read(CARDS_ASSET)
            val pricesRaw = assets.read(PRICES_ASSET)
            val imageSlugs = images.slugs()
            val version = BundledDataVersion.of(cardsRaw, pricesRaw, imageSlugs)
            if (dao.getCardCount() == 0 || dao.getMetadata(MetadataEntity.BUNDLED_DATA_VERSION) != version) {
                _syncState.value = SyncState.SyncingCards
                importBundledData(cardsRaw, pricesRaw, imageSlugs, version)
            }
            _syncState.value = SyncState.Complete
        } catch (e: Exception) {
            Log.w(TAG, "Bundled data import failed", e)
            _syncState.value = SyncState.Error(e.message ?: "Failed to load card catalogue")
        }
    }

    private suspend fun importBundledData(
        cardsRaw: String,
        pricesRaw: String,
        imageSlugs: Set<String>,
        version: String,
    ) {
        val cardList = json.decodeFromString<List<CardJson>>(cardsRaw)
        check(cardList.isNotEmpty()) { "The bundled card catalogue is empty" }
        val catalogue = CatalogueBuilder.build(cardList, imageSlugs)
        val prices = parsePrices(pricesRaw, catalogue.cards.mapTo(HashSet()) { it.name })

        dao.replaceBundledData(catalogue.sets, catalogue.cards, catalogue.variants, prices, version)

        val orphaned = dao.countOrphanedCollectionEntries()
        if (orphaned > 0) {
            Log.w(TAG, "$orphaned collection entries name cards missing from the catalogue; kept")
        }
    }

    /** Prices are optional: a bad price list leaves cards unpriced rather than failing the import. */
    private fun parsePrices(raw: String, cardNames: Set<String>): List<PriceEntity> =
        try {
            PriceSelector.select(json.decodeFromString<PricesJson>(raw).cards, cardNames)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load prices", e)
            emptyList()
        }

    override suspend fun getAllCardNames(): List<String> = dao.getAllCardNames()

    override suspend fun addToCollection(entries: List<CollectionEntryEntity>) {
        dao.upsertCollectionEntries(entries)
    }

    override suspend fun incrementInCollection(cardName: String) {
        dao.incrementCollectionEntry(cardName)
    }

    override suspend fun removeOneFromCollection(cardName: String) {
        dao.removeOneFromCollection(cardName)
    }

    override fun getCardByName(name: String) = dao.getCardByName(name)

    override fun getVariantsByCardName(cardName: String) = dao.getVariantsByCardName(cardName)

    private var faqCache: Map<String, List<FaqEntry>>? = null
    private val faqMutex = Mutex()

    override suspend fun getFaqs(cardName: String): List<FaqEntry> = withContext(Dispatchers.IO) {
        val cache = faqMutex.withLock {
            faqCache ?: try {
                val raw = assets.read("faqs.json")
                val map = json.decodeFromString<Map<String, List<FaqEntry>>>(raw)
                faqCache = map
                map
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load FAQs", e)
                emptyMap()
            }
        }
        cache[cardName].orEmpty()
    }

    private companion object {
        const val TAG = "CardRepository"
        const val CARDS_ASSET = "cards.json"
        const val PRICES_ASSET = "prices.json"
    }
}
