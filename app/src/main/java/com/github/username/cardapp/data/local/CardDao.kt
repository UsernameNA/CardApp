package com.github.username.cardapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE slug IN (SELECT MIN(slug) FROM cards GROUP BY name) ORDER BY name ASC")
    fun getUniqueCards(): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)
}
