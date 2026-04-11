package com.github.username.cardapp.data

import android.content.Context
import android.util.Log
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.remote.SorceryApi
import com.github.username.cardapp.ui.common.CardFilterState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: CardDao,
    private val api: SorceryApi,
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

    private val ensureDataOnce by lazy {
        appScope.launch {
            if (needsCardSync()) syncCards()
            loadPrices()
        }
    }

    override fun ensureDataLoaded() {
        ensureDataOnce
    }

    override suspend fun needsCardSync(): Boolean = dao.getCardCount() == 0

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
                val raw = context.assets.open("faqs.json").bufferedReader().use { it.readText() }
                val map = json.decodeFromString<Map<String, List<FaqEntry>>>(raw)
                faqCache = map
                map
            } catch (e: Exception) {
                Log.w("CardRepository", "Failed to load FAQs", e)
                emptyMap()
            }
        }
        cache[cardName].orEmpty()
    }

    override suspend fun loadPrices(): Unit = withContext(Dispatchers.IO) {
        try {
            val raw = context.assets.open("prices.json").bufferedReader().use { it.readText() }
            val data = json.decodeFromString<PricesJson>(raw)
            val map = mutableMapOf<String, PriceEntity>()
            val promoSets = setOf("dust reward promos", "arthurian legends promo")
            for (entry in data.cards) {
                val name = entry.productName ?: continue
                val market = entry.marketPrice ?: continue
                val isPromo = entry.setName?.lowercase() in promoSets
                val existing = map[name]
                if (existing == null ||
                    (existing.marketPrice != null && isPromo.not() && market < existing.marketPrice) ||
                    existing.marketPrice == null
                ) {
                    map[name] = PriceEntity(
                        cardName = name,
                        marketPrice = market,
                        lowPrice = entry.lowestPrice ?: market,
                    )
                }
            }
            dao.upsertPrices(map.values.toList())
        } catch (e: Exception) {
            Log.w("CardRepository", "Failed to load prices", e)
        }
    }

    private suspend fun fetchCardList(): List<CardJson> {
        return try {
            val remote = api.getCards()
            Log.d("CardRepository", "Fetched ${remote.size} cards from API")
            remote
        } catch (e: Exception) {
            Log.d("CardRepository", "API unavailable, using bundled data: ${e.message}")
            val raw = context.assets.open("cards.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<CardJson>>(raw)
        }
    }

    override suspend fun syncCards() = withContext(Dispatchers.IO) {
        val cardList = fetchCardList()

        val setMap = linkedMapOf<String, String>()
        for (card in cardList) {
            for (set in card.sets) {
                val name = set.name ?: continue
                if (name !in setMap) setMap[name] = set.releasedAt.orEmpty()
            }
        }
        val setEntities = setMap.entries
            .sortedBy { it.value }
            .mapIndexed { index, (name, releasedAt) ->
                SetEntity(name = name, releasedAt = releasedAt, releaseOrder = index + 1)
            }
        val setOrder = setEntities.associate { it.name to it.releaseOrder }

        val primarySlugOverrides = mapOf(
            "Apprentice Wizard" to "pro-apprentice_wizard-wk-s",
            "Grandmaster Wizard" to "pro-grandmaster_wizard-wk-s",
        )

        // Build setNames lookup: cardName -> ",Alpha,Beta,"
        val cardSetNames = mutableMapOf<String, MutableSet<String>>()
        for (card in cardList) {
            for (set in card.sets) {
                val setName = set.name ?: continue
                cardSetNames.getOrPut(card.name) { mutableSetOf() }.add(setName)
            }
        }

        val cardEntities = cardList.map { card ->
            val g = card.guardian
            val primarySlug = primarySlugOverrides[card.name]
                ?: card.sets
                    .sortedBy { setOrder[it.name] ?: Int.MAX_VALUE }
                    .firstNotNullOfOrNull { set ->
                        set.variants.firstOrNull { it.finish.equals("Standard", ignoreCase = true) }?.slug
                            ?: set.variants.firstOrNull()?.slug
                    }
                    .orEmpty()

            val setsForCard = cardSetNames[card.name]
            val setNamesStr = if (setsForCard.isNullOrEmpty()) "" else ",${setsForCard.joinToString(",")},";

            CardEntity(
                name = card.name,
                primarySlug = primarySlug,
                elements = card.elements.orEmpty(),
                subTypes = card.subTypes.orEmpty(),
                cardType = g?.type.orEmpty(),
                rarity = g?.rarity.orEmpty(),
                cost = g?.cost ?: 0,
                attack = g?.attack ?: 0,
                defence = g?.defence ?: 0,
                life = g?.life,
                rulesText = g?.rulesText.orEmpty(),
                airThreshold = g?.thresholds?.air ?: 0,
                earthThreshold = g?.thresholds?.earth ?: 0,
                fireThreshold = g?.thresholds?.fire ?: 0,
                waterThreshold = g?.thresholds?.water ?: 0,
                setNames = setNamesStr,
            )
        }

        val variantEntities = cardList.flatMap { card ->
            card.sets.flatMap { set ->
                set.variants.map { variant ->
                    CardVariantEntity(
                        slug = variant.slug,
                        cardName = card.name,
                        setName = set.name.orEmpty(),
                        finish = variant.finish.orEmpty(),
                        product = variant.product.orEmpty(),
                        artist = variant.artist.orEmpty(),
                        flavorText = variant.flavorText.orEmpty(),
                        typeText = variant.typeText.orEmpty(),
                    )
                }
            }
        }

        dao.syncAll(setEntities, cardEntities, variantEntities)
    }
}
