package com.github.username.cardapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SetEntity::class, CardEntity::class, CardVariantEntity::class, CollectionEntryEntity::class, PriceEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
