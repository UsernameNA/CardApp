package com.github.username.cardapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SetEntity::class,
        CardEntity::class,
        CardVariantEntity::class,
        CollectionEntryEntity::class,
        PriceEntity::class,
        MetadataEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        const val NAME = "cardapp.db"

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*DatabaseMigrations.ALL)
                // Pre-collection schemas (v1–v3, before CollectionEntryEntity existed at v4)
                // hold only the re-importable card catalogue, so wiping them loses nothing.
                // From v4 onward a real migration is REQUIRED — a missing one throws at
                // startup instead of silently destroying the user's collection.
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                .build()
    }
}
