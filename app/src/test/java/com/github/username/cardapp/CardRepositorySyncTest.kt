package com.github.username.cardapp

import com.github.username.cardapp.data.CardRepositoryImpl
import com.github.username.cardapp.data.SyncState
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.remote.SorceryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRepositorySyncTest {

    private fun api(getCards: () -> List<CardJson>): SorceryApi = object : SorceryApi {
        override suspend fun getCards(): List<CardJson> = getCards()
    }

    private fun repository(dao: CardDao, api: SorceryApi) = CardRepositoryImpl(
        assets = { fileName -> throw IllegalStateException("no asset: $fileName") },
        dao = dao,
        api = api,
        json = Json { ignoreUnknownKeys = true },
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private fun awaitFinalState(repo: CardRepositoryImpl): SyncState = runBlocking {
        repo.ensureDataLoaded()
        withTimeout(5_000) {
            repo.syncState.first { it is SyncState.Complete || it is SyncState.Error }
        }
    }

    @Test
    fun successfulSyncReachesCompleteState() {
        val dao = FakeCardDao(cardCount = 0)
        val repo = repository(dao, api = api { listOf(CardJson(name = "Ruby Core")) })

        val state = awaitFinalState(repo)

        assertEquals(SyncState.Complete, state)
        assertEquals(listOf("Ruby Core"), dao.syncedCards?.map { it.name })
    }

    @Test
    fun failedSyncReachesErrorStateInsteadOfCrashing() {
        val dao = FakeCardDao(cardCount = 0)
        // API fails and the bundled-asset fallback fails too.
        val repo = repository(dao, api = api { throw IllegalStateException("network down") })

        val state = awaitFinalState(repo)

        assertTrue("expected Error but was $state", state is SyncState.Error)
    }

    @Test
    fun populatedDatabaseSkipsSyncAndCompletes() {
        val dao = FakeCardDao(cardCount = 42)
        val repo = repository(dao, api = api { throw IllegalStateException("should not be called") })

        val state = awaitFinalState(repo)

        assertEquals(SyncState.Complete, state)
        assertEquals(null, dao.syncedCards)
    }
}
