package com.github.username.cardapp

import com.github.username.cardapp.data.CardRepositoryImpl
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.remote.SorceryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync gate decides whether an install rebuilds its catalogue on launch.
 *
 * Getting this wrong is invisible in the emulator (fresh installs always sync) but breaks
 * every card image on an upgrade, so it is worth pinning down.
 */
class CardRepositorySyncGateTest {

    private fun repo(dao: FakeCardDao) = CardRepositoryImpl(
        assets = { throw IllegalStateException("no asset access in this test") },
        dao = dao,
        api = object : SorceryApi {
            override suspend fun getCards(): List<CardJson> = emptyList()
        },
        json = Json { ignoreUnknownKeys = true },
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    @Test
    fun emptyCatalogueSyncs() = runBlocking {
        assertTrue(repo(FakeCardDao(cardCount = 0)).needsCardSync())
    }

    @Test
    fun populatedCurrentCatalogueDoesNotSync() = runBlocking {
        val dao = FakeCardDao(cardCount = 1100, hasLegacySlugs = false)
        assertFalse(repo(dao).needsCardSync())
    }

    @Test
    fun catalogueWithPre2026SlugsResyncs() = runBlocking {
        // Upgraders whose DB still holds "alp-…" slugs must rebuild, otherwise every
        // image lookup misses the renamed asset files.
        val dao = FakeCardDao(cardCount = 1104, hasLegacySlugs = true)
        assertTrue(repo(dao).needsCardSync())
    }
}
