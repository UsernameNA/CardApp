package com.github.username.cardapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY name ASC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM sets ORDER BY releaseOrder ASC")
    fun getAllSets(): Flow<List<SetEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE name = :name LIMIT 1")
    fun getCardByName(name: String): Flow<CardEntity?>

    @RawQuery(observedEntities = [CardEntity::class, PriceEntity::class])
    fun filteredCards(query: SupportSQLiteQuery): Flow<List<CardWithPrice>>

    @RawQuery(observedEntities = [CardEntity::class, PriceEntity::class, CollectionEntryEntity::class])
    fun filteredCollectionCards(query: SupportSQLiteQuery): Flow<List<CollectionCardWithPrice>>

    @Query("SELECT * FROM variants WHERE cardName = :cardName ORDER BY setName, finish")
    fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSets(sets: List<SetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCards(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVariants(variants: List<CardVariantEntity>)

    @Query("DELETE FROM variants")
    suspend fun deleteAllVariants()

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    @Query("DELETE FROM sets")
    suspend fun deleteAllSets()

    @Transaction
    suspend fun syncAll(
        sets: List<SetEntity>,
        cards: List<CardEntity>,
        variants: List<CardVariantEntity>,
    ) {
        deleteAllVariants()
        deleteAllCards()
        deleteAllSets()
        insertAllSets(sets)
        insertAllCards(cards)
        insertAllVariants(variants)
    }

    // --- Collection ---

    @Transaction
    @Query("""
        SELECT c.*, ce.quantity FROM collection ce
        INNER JOIN cards c ON c.name = ce.cardName
        ORDER BY c.name ASC
    """)
    fun getCollectionEntries(): Flow<List<CollectionCardRow>>

    @Query("""
        INSERT INTO collection (cardName, quantity)
        VALUES (:cardName, :quantity)
        ON CONFLICT(cardName) DO UPDATE SET quantity = quantity + excluded.quantity
    """)
    suspend fun upsertCollectionEntry(cardName: String, quantity: Int)

    @Transaction
    suspend fun upsertCollectionEntries(entries: List<CollectionEntryEntity>) {
        for (entry in entries) upsertCollectionEntry(entry.cardName, entry.quantity)
    }

    @Query("""
        INSERT INTO collection (cardName, quantity)
        VALUES (:cardName, 1)
        ON CONFLICT(cardName) DO UPDATE SET quantity = quantity + 1
    """)
    suspend fun incrementCollectionEntry(cardName: String)

    @Query("UPDATE collection SET quantity = quantity - 1 WHERE cardName = :cardName AND quantity > 1")
    suspend fun decrementCollectionEntry(cardName: String): Int

    @Query("DELETE FROM collection WHERE cardName = :cardName AND quantity <= 1")
    suspend fun deleteCollectionEntryIfSingle(cardName: String): Int

    @Transaction
    suspend fun removeOneFromCollection(cardName: String) {
        val deleted = deleteCollectionEntryIfSingle(cardName)
        if (deleted == 0) decrementCollectionEntry(cardName)
    }

    // --- Prices ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrices(prices: List<PriceEntity>)

    @Query("SELECT * FROM prices")
    fun getAllPrices(): Flow<List<PriceEntity>>
}
