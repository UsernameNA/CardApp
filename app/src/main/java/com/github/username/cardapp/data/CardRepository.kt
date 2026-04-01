package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.data.local.CollectionEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CardRepository {
    val cards: Flow<List<CardEntity>>
    val collection: Flow<List<CollectionCardRow>>
    val prices: StateFlow<Map<String, PriceInfo>>
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
