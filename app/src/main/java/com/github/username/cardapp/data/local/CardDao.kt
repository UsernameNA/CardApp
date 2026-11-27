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

    @Query("SELECT * FROM sets ORDER BY releaseOrder ASC")
    fun getAllSets(): Flow<List<SetEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("SELECT name FROM cards")
    suspend fun getAllCardNames(): List<String>

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

    @Query("DELETE FROM prices")
    suspend fun deleteAllPrices()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<PriceEntity>)

    /**
     * Swap in a freshly imported catalogue and price list, recording [dataVersion] in the
     * same transaction so an interrupted import is simply redone on the next launch.
     * The collection is not touched (it has no foreign key to cards).
     */
    @Transaction
    suspend fun replaceBundledData(
        sets: List<SetEntity>,
        cards: List<CardEntity>,
        variants: List<CardVariantEntity>,
        prices: List<PriceEntity>,
        dataVersion: String,
    ) {
        deleteAllVariants()
        deleteAllCards()
        deleteAllSets()
        deleteAllPrices()
        insertAllSets(sets)
        insertAllCards(cards)
        insertAllVariants(variants)
        insertPrices(prices)
        putMetadata(MetadataEntity(MetadataEntity.BUNDLED_DATA_VERSION, dataVersion))
    }

    // --- Metadata ---

    @Query("SELECT value FROM metadata WHERE name = :name")
    suspend fun getMetadata(name: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(entry: MetadataEntity)

    // --- Collection ---

    /** Copies owned of cards in the catalogue; orphaned entries are hidden, as everywhere else. */
    @Query("""
        SELECT COALESCE(SUM(ce.quantity), 0) FROM collection ce
        INNER JOIN cards c ON c.name = ce.cardName
    """)
    fun observeCollectionQuantity(): Flow<Int>

    @Query("""
        INSERT INTO collection (cardName, quantity)
        VALUES (:cardName, MIN(:quantity, ${CollectionEntryEntity.MAX_QUANTITY}))
        ON CONFLICT(cardName) DO UPDATE
            SET quantity = MIN(quantity + excluded.quantity, ${CollectionEntryEntity.MAX_QUANTITY})
    """)
    suspend fun upsertCollectionEntry(cardName: String, quantity: Int)

    @Transaction
    suspend fun upsertCollectionEntries(entries: List<CollectionEntryEntity>) {
        for (entry in entries) upsertCollectionEntry(entry.cardName, entry.quantity)
    }

    @Query("""
        INSERT INTO collection (cardName, quantity)
        VALUES (:cardName, 1)
        ON CONFLICT(cardName) DO UPDATE SET quantity = MIN(quantity + 1, ${CollectionEntryEntity.MAX_QUANTITY})
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

    /** Collection entries whose card is not in the current catalogue. */
    @Query("SELECT COUNT(*) FROM collection WHERE cardName NOT IN (SELECT name FROM cards)")
    suspend fun countOrphanedCollectionEntries(): Int

    // --- Prices ---

    @Query("SELECT * FROM prices")
    fun getAllPrices(): Flow<List<PriceEntity>>

    @Query("SELECT marketPrice FROM prices WHERE cardName = :cardName")
    fun observeMarketPrice(cardName: String): Flow<Double?>
}
