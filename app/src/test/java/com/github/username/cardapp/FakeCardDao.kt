package com.github.username.cardapp

import androidx.sqlite.db.SupportSQLiteQuery
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.MetadataEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.data.local.SetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory CardDao for JVM unit tests; records bundled-data imports. */
class FakeCardDao(
    private var cardCount: Int = 0,
    dataVersion: String? = null,
) : CardDao {
    private val metadata = mutableMapOf<String, String>()

    init {
        if (dataVersion != null) metadata[MetadataEntity.BUNDLED_DATA_VERSION] = dataVersion
    }

    var importCount = 0
        private set
    var importedCards: List<CardEntity>? = null
        private set
    var importedPrices: List<PriceEntity>? = null
        private set

    val storedDataVersion: String? get() = metadata[MetadataEntity.BUNDLED_DATA_VERSION]

    override fun getAllSets(): Flow<List<SetEntity>> = flowOf(emptyList())
    override suspend fun getCardCount(): Int = cardCount
    override suspend fun getAllCardNames(): List<String> = importedCards.orEmpty().map { it.name }
    override fun observeCardCount(): Flow<Int> = flowOf(cardCount)
    override fun getCardByName(name: String): Flow<CardEntity?> = flowOf(null)
    override fun filteredCards(query: SupportSQLiteQuery): Flow<List<CardWithPrice>> = flowOf(emptyList())
    override fun filteredCollectionCards(query: SupportSQLiteQuery): Flow<List<CollectionCardWithPrice>> =
        flowOf(emptyList())
    override fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>> = flowOf(emptyList())
    override suspend fun insertAllSets(sets: List<SetEntity>) {}
    override suspend fun insertAllCards(cards: List<CardEntity>) {}
    override suspend fun insertAllVariants(variants: List<CardVariantEntity>) {}
    override suspend fun deleteAllVariants() {}
    override suspend fun deleteAllCards() {}
    override suspend fun deleteAllSets() {}
    override suspend fun deleteAllPrices() {}
    override suspend fun insertPrices(prices: List<PriceEntity>) {}

    override suspend fun replaceBundledData(
        sets: List<SetEntity>,
        cards: List<CardEntity>,
        variants: List<CardVariantEntity>,
        prices: List<PriceEntity>,
        dataVersion: String,
    ) {
        importCount++
        importedCards = cards
        importedPrices = prices
        cardCount = cards.size
        metadata[MetadataEntity.BUNDLED_DATA_VERSION] = dataVersion
    }

    override suspend fun getMetadata(name: String): String? = metadata[name]
    override suspend fun putMetadata(entry: MetadataEntity) {
        metadata[entry.name] = entry.value
    }
    override fun observeCollectionQuantity(): Flow<Int> = flowOf(0)
    override suspend fun upsertCollectionEntry(cardName: String, quantity: Int) {}
    override suspend fun incrementCollectionEntry(cardName: String) {}
    override suspend fun decrementCollectionEntry(cardName: String): Int = 0
    override suspend fun deleteCollectionEntryIfSingle(cardName: String): Int = 0
    override suspend fun countOrphanedCollectionEntries(): Int = 0
    override fun getAllPrices(): Flow<List<PriceEntity>> = flowOf(emptyList())
    override fun observeMarketPrice(cardName: String): Flow<Double?> = flowOf(null)
}
