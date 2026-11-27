package com.github.username.cardapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.username.cardapp.data.AssetTextReader
import com.github.username.cardapp.data.CardRepositoryImpl
import com.github.username.cardapp.data.CatalogueBuilder
import com.github.username.cardapp.data.FaqEntry
import com.github.username.cardapp.data.ImportState
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.model.CardJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs the real bundled assets through the production import into a real Room database.
 * The data changes every release, so this checks invariants rather than exact counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BundledDataImportTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val assetDir = File("src/main/assets")
    private val imageSlugs = assetDir.resolve("images").list().orEmpty()
        .filter { it.endsWith(".webp") }
        .mapTo(HashSet()) { it.removeSuffix(".webp") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun asset(name: String) = assetDir.resolve(name).readText()

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun <T> assertUnique(what: String, keys: List<T>) {
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("duplicate $what would be silently replaced on insert: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun bundledAssetsImportCleanly() {
        val catalogue = CatalogueBuilder.build(json.decodeFromString<List<CardJson>>(asset("cards.json")), imageSlugs)
        assertUnique("card names", catalogue.cards.map { it.name })
        assertUnique("variant slugs", catalogue.variants.map { it.slug })
        assertUnique("set names", catalogue.sets.map { it.name })
        val setNames = catalogue.sets.mapTo(HashSet()) { it.name }
        val orphanVariants = catalogue.variants.filter { it.setName !in setNames }.map { it.slug }
        assertTrue("variants naming a set that doesn't exist: $orphanVariants", orphanVariants.isEmpty())

        val repository = CardRepositoryImpl(
            assets = AssetTextReader(::asset),
            images = { imageSlugs },
            dao = db.cardDao(),
            json = json,
            appScope = scope,
        )
        val state = runBlocking {
            repository.ensureDataLoaded()
            withTimeout(60_000) {
                repository.importState.first { it is ImportState.Complete || it is ImportState.Error }
            }
        }
        assertEquals(ImportState.Complete, state)

        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("foreign key violations after import", 0, cursor.count)
        }
        assertEquals("every card row was stored", catalogue.cards.size, count("cards"))
        assertEquals("every variant row was stored", catalogue.variants.size, count("variants"))
        assertEquals("every set row was stored", catalogue.sets.size, count("sets"))
        assertTrue("expected a full catalogue, got ${count("cards")} cards", count("cards") > 1000)
        assertTrue("expected variants for every card", count("variants") >= count("cards"))
        assertTrue("expected the price list to import, got ${count("prices")} prices", count("prices") > 500)
        assertEquals(
            "prices for cards outside the catalogue",
            0,
            db.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM prices WHERE cardName NOT IN (SELECT name FROM cards)")
                .use { it.moveToFirst(); it.getInt(0) },
        )
        assertEquals(
            "cards without folded search/sort keys",
            0,
            db.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM cards WHERE searchKey = '' OR sortKey = ''")
                .use { it.moveToFirst(); it.getInt(0) },
        )
    }

    @Test
    fun everyFaqIsKeyedByACardName() {
        val cardNames = json.decodeFromString<List<CardJson>>(asset("cards.json")).mapTo(HashSet()) { it.name }
        val faqs = json.decodeFromString<Map<String, List<FaqEntry>>>(asset("faqs.json"))

        assertTrue("expected bundled FAQs", faqs.isNotEmpty())
        val unknown = faqs.keys - cardNames
        assertTrue("FAQ keys that are not card names: $unknown", unknown.isEmpty())
    }
}
