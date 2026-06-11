package com.github.username.cardapp

import com.github.username.cardapp.data.CardRepositoryImpl
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.remote.SorceryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CardRepositoryPricesTest {

    private fun loadPrices(pricesJson: String, dao: FakeCardDao) {
        val repo = CardRepositoryImpl(
            assets = { fileName ->
                if (fileName == "prices.json") pricesJson else throw IllegalStateException("no asset: $fileName")
            },
            dao = dao,
            api = object : SorceryApi {
                override suspend fun getCards(): List<CardJson> = emptyList()
            },
            json = Json { ignoreUnknownKeys = true },
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
        runBlocking { repo.loadPrices() }
    }

    private fun entry(name: String, set: String, market: Double): String =
        """{"productName":"$name","setName":"$set","marketPrice":$market,"lowestPrice":$market}"""

    private fun marketPriceFor(dao: FakeCardDao, name: String): Double? =
        dao.upsertedPrices?.firstOrNull { it.cardName == name }?.marketPrice

    @Test
    fun nonPromoPriceBeatsEarlierCheaperPromoPrice() {
        val json = """{"cards":[
            ${entry("Ruby Core", "Arthurian Legends Promo", 1.0)},
            ${entry("Ruby Core", "Alpha", 5.0)}
        ]}"""
        val dao = FakeCardDao()
        loadPrices(json, dao)
        assertEquals(5.0, marketPriceFor(dao, "Ruby Core"))
    }

    @Test
    fun promoNeverReplacesNonPromoEvenIfCheaper() {
        val json = """{"cards":[
            ${entry("Ruby Core", "Alpha", 5.0)},
            ${entry("Ruby Core", "Dust Reward Promos", 1.0)}
        ]}"""
        val dao = FakeCardDao()
        loadPrices(json, dao)
        assertEquals(5.0, marketPriceFor(dao, "Ruby Core"))
    }

    @Test
    fun lowestNonPromoPriceWins() {
        val json = """{"cards":[
            ${entry("Ruby Core", "Alpha", 5.0)},
            ${entry("Ruby Core", "Beta", 3.0)},
            ${entry("Ruby Core", "Gamma", 4.0)}
        ]}"""
        val dao = FakeCardDao()
        loadPrices(json, dao)
        assertEquals(3.0, marketPriceFor(dao, "Ruby Core"))
    }

    @Test
    fun promoPriceUsedWhenOnlyPromoPrintingExists() {
        val json = """{"cards":[${entry("Ruby Core", "Arthurian Legends Promo", 2.0)}]}"""
        val dao = FakeCardDao()
        loadPrices(json, dao)
        assertEquals(2.0, marketPriceFor(dao, "Ruby Core"))
    }
}
