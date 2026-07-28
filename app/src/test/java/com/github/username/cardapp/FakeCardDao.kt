package com.github.username.cardapp

import androidx.sqlite.db.SupportSQLiteQuery
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.data.local.SetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory CardDao for JVM unit tests; records what was written. */
class FakeCardDao(
    private var cardCount: Int = 0,
    private var hasLegacySlugs: Boolean = false,
) : CardDao {
    var syncedCards: List<CardEntity>? = null
    var upsertedPrices: List<PriceEntity>? = null

    override fun getAllCards(): Flow<List<CardEntity>> = flowOf(emptyList())
    override fun getAllSets(): Flow<List<SetEntity>> = flowOf(emptyList())
    override suspend fun getCardCount(): Int = cardCount
    override suspend fun hasLegacySlugs(): Boolean = hasLegacySlugs
    override suspend fun getAllCardNames(): List<String> = emptyList()
    override fun observeCardCount(): Flow<Int> = flowOf(cardCount)
    override fun getCardByName(name: String): Flow<CardEntity?> = flowOf(null)
    override fun filteredCards(query: SupportSQLiteQuery): Flow<List<CardWithPrice>> = flowOf(emptyList())
    override fun filteredCollectionCards(query: SupportSQLiteQuery): Flow<List<CollectionCardWithPrice>> =
        flowOf(emptyList())
    override fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>> = flowOf(emptyList())
    override suspend fun insertAllSets(sets: List<SetEntity>) {}
    override suspend fun insertAllCards(cards: List<CardEntity>) {
        syncedCards = cards
        cardCount = cards.size
    }
    override suspend fun insertAllVariants(variants: List<CardVariantEntity>) {}
    override suspend fun deleteAllVariants() {}
    override suspend fun deleteAllCards() {}
    override suspend fun deleteAllSets() {}
    override fun getCollectionEntries(): Flow<List<CollectionCardRow>> = flowOf(emptyList())
    override suspend fun getAllCollectionEntriesOnce(): List<CollectionEntryEntity> = emptyList()
    override suspend fun insertCollectionEntries(entries: List<CollectionEntryEntity>) {}
    override suspend fun upsertCollectionEntry(cardName: String, quantity: Int) {}
    override suspend fun incrementCollectionEntry(cardName: String) {}
    override suspend fun decrementCollectionEntry(cardName: String): Int = 0
    override suspend fun deleteCollectionEntryIfSingle(cardName: String): Int = 0
    override suspend fun upsertPrices(prices: List<PriceEntity>) {
        upsertedPrices = prices
    }
    override fun getAllPrices(): Flow<List<PriceEntity>> = flowOf(emptyList())
}
