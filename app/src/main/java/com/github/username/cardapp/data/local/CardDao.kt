package com.github.username.cardapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY name ASC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM sets ORDER BY releaseOrder ASC")
    suspend fun getAllSets(): List<SetEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSets(sets: List<SetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCards(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVariants(variants: List<CardVariantEntity>)
}
