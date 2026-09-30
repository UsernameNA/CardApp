package com.github.username.cardapp

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.github.username.cardapp.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrades real on-disk databases from every schema a shipped release used and opens them
 * the way the app does, so Room's post-migration schema validation runs for real.
 *
 * v4 shipped in v1.0.0–v1.2.2, v5 in v1.3.0, v6 in v1.4.0–v1.5.0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** v4 predates schema export; this is Room's DDL for the v1.2.2 entities. */
    private val v4Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `sets` (`name` TEXT NOT NULL, `releasedAt` TEXT NOT NULL, " +
            "`releaseOrder` INTEGER NOT NULL, PRIMARY KEY(`name`))",
        "CREATE TABLE IF NOT EXISTS `cards` (`name` TEXT NOT NULL, `primarySlug` TEXT NOT NULL, " +
            "`elements` TEXT NOT NULL, `subTypes` TEXT NOT NULL, `cardType` TEXT NOT NULL, " +
            "`rarity` TEXT NOT NULL, `cost` INTEGER NOT NULL, `attack` INTEGER NOT NULL, " +
            "`defence` INTEGER NOT NULL, `life` INTEGER, `rulesText` TEXT NOT NULL, " +
            "`airThreshold` INTEGER NOT NULL, `earthThreshold` INTEGER NOT NULL, " +
            "`fireThreshold` INTEGER NOT NULL, `waterThreshold` INTEGER NOT NULL, PRIMARY KEY(`name`))",
        "CREATE TABLE IF NOT EXISTS `variants` (`slug` TEXT NOT NULL, `cardName` TEXT NOT NULL, " +
            "`setName` TEXT NOT NULL, `finish` TEXT NOT NULL, `product` TEXT NOT NULL, " +
            "`artist` TEXT NOT NULL, `flavorText` TEXT NOT NULL, `typeText` TEXT NOT NULL, " +
            "PRIMARY KEY(`slug`), FOREIGN KEY(`cardName`) REFERENCES `cards`(`name`) ON UPDATE NO ACTION " +
            "ON DELETE CASCADE , FOREIGN KEY(`setName`) REFERENCES `sets`(`name`) ON UPDATE NO ACTION " +
            "ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_variants_cardName` ON `variants` (`cardName`)",
        "CREATE INDEX IF NOT EXISTS `index_variants_setName` ON `variants` (`setName`)",
        "CREATE TABLE IF NOT EXISTS `collection` (`cardName` TEXT NOT NULL, `quantity` INTEGER NOT NULL, " +
            "PRIMARY KEY(`cardName`), FOREIGN KEY(`cardName`) REFERENCES `cards`(`name`) ON UPDATE NO ACTION " +
            "ON DELETE CASCADE )",
    )

    /** DDL for an exported schema version, straight from app/schemas/. */
    private fun exportedSchema(version: Int): List<String> {
        val file = File("schemas/com.github.username.cardapp.data.local.AppDatabase/$version.json")
        val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
        return database.getValue("entities").jsonArray.flatMap { entity ->
            val obj = entity.jsonObject
            val table = obj.getValue("tableName").jsonPrimitive.content
            val create = obj.getValue("createSql").jsonPrimitive.content
            val indices = obj["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content }
            (listOf(create) + indices).map { it.replace("\${TABLE_NAME}", table) }
        }
    }

    private fun sampleRows(withSetNames: Boolean): List<String> {
        val cardColumns = "name, primarySlug, elements, subTypes, cardType, rarity, cost, attack, defence, life, " +
            "rulesText, airThreshold, earthThreshold, fireThreshold, waterThreshold" + if (withSetNames) ", setNames" else ""
        val cardValues = "'Ruby Core', '001-ruby_core-a-s', '', '', 'Artifact', 'Ordinary', 0, 0, 0, NULL, " +
            "'', 0, 0, 0, 0" + if (withSetNames) ", ',Alpha,'" else ""
        return listOf(
            "INSERT INTO sets (name, releasedAt, releaseOrder) VALUES ('Alpha', '2023-01-01', 1)",
            "INSERT INTO cards ($cardColumns) VALUES ($cardValues)",
            "INSERT INTO variants (slug, cardName, setName, finish, product, artist, flavorText, typeText) " +
                "VALUES ('001-ruby_core-a-s', 'Ruby Core', 'Alpha', 'Standard', '', '', '', '')",
            "INSERT INTO collection (cardName, quantity) VALUES ('Ruby Core', 3)",
        )
    }

    private fun createDatabase(version: Int, statements: List<String>) {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements.forEach(db::execSQL)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v$version')")
            db.version = version
        }
    }

    /** Open with the production builder and check the collection came through intact. */
    private fun assertCollectionSurvivesUpgrade() {
        val db = AppDatabase.create(context, name)
        try {
            val entries = runBlocking { db.cardDao().getCollectionEntries().first() }
            assertEquals(listOf("Ruby Core" to 3), entries.map { it.card.name to it.quantity })

            val sqlite = db.openHelper.writableDatabase
            sqlite.query("PRAGMA foreign_key_list(collection)").use { cursor ->
                assertEquals("the collection must not cascade from cards", 0, cursor.count)
            }
            sqlite.query("SELECT setNames FROM cards").use { cursor ->
                cursor.moveToFirst()
                assertEquals(",Alpha,", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun upgradesFromV4() {
        createDatabase(4, v4Schema + sampleRows(withSetNames = false))
        assertCollectionSurvivesUpgrade()
    }

    @Test
    fun upgradesFromV5() {
        createDatabase(5, exportedSchema(5) + sampleRows(withSetNames = true))
        assertCollectionSurvivesUpgrade()
    }

    @Test
    fun upgradesFromV6() {
        createDatabase(6, exportedSchema(6) + sampleRows(withSetNames = true))
        assertCollectionSurvivesUpgrade()
    }
}
