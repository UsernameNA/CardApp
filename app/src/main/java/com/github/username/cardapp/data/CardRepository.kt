package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.common.CardFilterState
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>>
    fun filteredCollection(filter: CardFilterState): Flow<List<CollectionCardWithPrice>>
    val sets: Flow<List<SetEntity>>
    val priceMap: Flow<Map<String, Double>>
    val totalCardCount: Flow<Int>
    val totalCollectionQuantity: Flow<Int>
    suspend fun needsCardSync(): Boolean
    suspend fun syncCards()
    suspend fun loadPrices()
    suspend fun addToCollection(entries: List<CollectionEntryEntity>)
    suspend fun incrementInCollection(cardName: String)
    suspend fun removeOneFromCollection(cardName: String)
    fun getCardByName(name: String): Flow<CardEntity?>
    fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>>
    suspend fun getFaqs(cardName: String): List<FaqEntry>
    fun ensureDataLoaded()
}
