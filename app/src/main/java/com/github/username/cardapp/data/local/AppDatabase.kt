package com.github.username.cardapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SetEntity::class, CardEntity::class, CardVariantEntity::class, CollectionEntryEntity::class, PriceEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
