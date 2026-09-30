package com.github.username.cardapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations from v4 (the first schema holding the user's collection) onward.
 *
 * Room validates the migrated schema against the entities, indices included, and throws
 * on any mismatch — so a migration must create exactly what the entities declare, no more.
 */
object DatabaseMigrations {

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cards ADD COLUMN setNames TEXT NOT NULL DEFAULT ''")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS prices (
                    cardName TEXT NOT NULL PRIMARY KEY,
                    marketPrice REAL,
                    lowPrice REAL
                )
            """)
            db.execSQL("""
                UPDATE cards SET setNames = COALESCE(
                    (SELECT ',' || GROUP_CONCAT(DISTINCT setName) || ',' FROM variants WHERE variants.cardName = cards.name),
                    ''
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_variants_cardName ON variants (cardName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_variants_setName ON variants (setName)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_cardName ON collection (cardName)")
        }
    }

    /** Detach the collection from the catalogue (no FK) and add the metadata table. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `collection_new` (`cardName` TEXT NOT NULL, " +
                    "`quantity` INTEGER NOT NULL, PRIMARY KEY(`cardName`))"
            )
            db.execSQL("INSERT INTO collection_new (cardName, quantity) SELECT cardName, quantity FROM collection")
            db.execSQL("DROP TABLE collection")
            db.execSQL("ALTER TABLE collection_new RENAME TO collection")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `metadata` (`name` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, PRIMARY KEY(`name`))"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
