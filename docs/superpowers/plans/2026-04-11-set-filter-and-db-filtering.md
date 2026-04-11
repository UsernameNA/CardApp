# Set Filter & Database-Level Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add set filtering to Cards/Collection screens and migrate all filtering from in-memory Kotlin to Room SQL queries, including moving prices to a database table.

**Architecture:** Replace the in-memory `applyFilter()` pipeline with a `FilterQueryBuilder` that produces `SupportSQLiteQuery` objects executed by Room. A new `PriceEntity` table replaces the in-memory price map. `CardEntity` gains a denormalized `setNames` column for efficient set filtering without JOINs. ViewModels switch from `combine + applyFilter()` to `flatMapLatest { repository.filteredCards(filter) }`.

**Tech Stack:** Room (RawQuery, Migration), Kotlin Flow (flatMapLatest), Jetpack Compose, Hilt

**Spec:** `docs/superpowers/specs/2026-04-11-set-filter-and-db-filtering-design.md`

---

### Task 1: Data Models — PriceEntity, CardEntity.setNames, Result Types

**Files:**
- Create: `app/src/main/java/com/github/username/cardapp/data/local/PriceEntity.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/data/local/CardEntity.kt`
- Create: `app/src/main/java/com/github/username/cardapp/data/local/CardWithPrice.kt`
- Create: `app/src/main/java/com/github/username/cardapp/data/local/CollectionCardWithPrice.kt`

- [ ] **Step 1: Create PriceEntity**

```kotlin
// app/src/main/java/com/github/username/cardapp/data/local/PriceEntity.kt
package com.github.username.cardapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prices")
data class PriceEntity(
    @PrimaryKey val cardName: String,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
```

- [ ] **Step 2: Add setNames to CardEntity**

In `CardEntity.kt`, add the `setNames` field after `waterThreshold`:

```kotlin
// Add this field at the end of the data class, after waterThreshold:
    val setNames: String = "",
```

The full data class becomes:

```kotlin
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val name: String,
    val primarySlug: String,
    val elements: String,
    val subTypes: String,
    val cardType: String,
    val rarity: String,
    val cost: Int,
    val attack: Int,
    val defence: Int,
    val life: Int?,
    val rulesText: String,
    val airThreshold: Int,
    val earthThreshold: Int,
    val fireThreshold: Int,
    val waterThreshold: Int,
    val setNames: String = "",
)
```

- [ ] **Step 3: Create CardWithPrice result type**

```kotlin
// app/src/main/java/com/github/username/cardapp/data/local/CardWithPrice.kt
package com.github.username.cardapp.data.local

import androidx.room.Embedded

data class CardWithPrice(
    @Embedded val card: CardEntity,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
```

- [ ] **Step 4: Create CollectionCardWithPrice result type**

```kotlin
// app/src/main/java/com/github/username/cardapp/data/local/CollectionCardWithPrice.kt
package com.github.username.cardapp.data.local

import androidx.room.Embedded

data class CollectionCardWithPrice(
    @Embedded val card: CardEntity,
    val quantity: Int,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/data/local/PriceEntity.kt \
       app/src/main/java/com/github/username/cardapp/data/local/CardEntity.kt \
       app/src/main/java/com/github/username/cardapp/data/local/CardWithPrice.kt \
       app/src/main/java/com/github/username/cardapp/data/local/CollectionCardWithPrice.kt
git commit -m "feat: add PriceEntity, CardEntity.setNames, and query result types"
```

---

### Task 2: Room Migration + AppDatabase Update

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/di/DatabaseModule.kt` (or wherever the database is built — check for `Room.databaseBuilder`)

- [ ] **Step 1: Find the database builder**

Search for `Room.databaseBuilder` to find where the database is constructed. This is where the migration will be registered.

```bash
grep -rn "Room.databaseBuilder" app/src/main/java/
```

- [ ] **Step 2: Update AppDatabase**

In `AppDatabase.kt`, add `PriceEntity` to the entities list and bump the version to 5:

```kotlin
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
```

- [ ] **Step 3: Add migration to the database builder**

In the file that builds the database (found in step 1), add the migration. Import `androidx.room.migration.Migration` and `androidx.sqlite.db.SupportSQLiteDatabase`:

```kotlin
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add setNames column to cards
        db.execSQL("ALTER TABLE cards ADD COLUMN setNames TEXT NOT NULL DEFAULT ''")

        // Create prices table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS prices (
                cardName TEXT NOT NULL PRIMARY KEY,
                marketPrice REAL,
                lowPrice REAL
            )
        """)

        // Populate setNames from variants (comma-delimited with sentinels)
        db.execSQL("""
            UPDATE cards SET setNames = COALESCE(
                (SELECT ',' || GROUP_CONCAT(DISTINCT setName) || ',' FROM variants WHERE variants.cardName = cards.name),
                ''
            )
        """)

        // Add indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_cardType ON cards (cardType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_rarity ON cards (rarity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_variants_cardName ON variants (cardName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_variants_setName ON variants (setName)")
    }
}
```

Register it on the builder: `.addMigrations(MIGRATION_4_5)` (chain before `.build()`).

- [ ] **Step 4: Verify the app compiles**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/data/local/AppDatabase.kt \
       <database-builder-file>
git commit -m "feat: Room migration v4→v5 — prices table, setNames column, indexes"
```

---

### Task 3: CardDao Updates

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/data/local/CardDao.kt`

- [ ] **Step 1: Add new imports and query methods**

Add these imports at the top of `CardDao.kt`:

```kotlin
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
```

- [ ] **Step 2: Add filteredCards RawQuery**

Add after the existing `getCardByName` method:

```kotlin
@RawQuery(observedEntities = [CardEntity::class, PriceEntity::class])
fun filteredCards(query: SupportSQLiteQuery): Flow<List<CardWithPrice>>

@RawQuery(observedEntities = [CardEntity::class, PriceEntity::class, CollectionEntryEntity::class])
fun filteredCollectionCards(query: SupportSQLiteQuery): Flow<List<CollectionCardWithPrice>>
```

- [ ] **Step 3: Change getAllSets to return Flow**

Replace the existing `getAllSets()`:

```kotlin
@Query("SELECT * FROM sets ORDER BY releaseOrder ASC")
fun getAllSets(): Flow<List<SetEntity>>
```

(Change from `suspend fun` returning `List` to `fun` returning `Flow`.)

- [ ] **Step 4: Add price operations**

Add after the collection methods:

```kotlin
// --- Prices ---

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertPrices(prices: List<PriceEntity>)

@Query("SELECT * FROM prices")
fun getAllPrices(): Flow<List<PriceEntity>>
```

- [ ] **Step 5: Verify compilation**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/data/local/CardDao.kt
git commit -m "feat: CardDao — add RawQuery for filtered cards, price ops, Flow-based sets"
```

---

### Task 4: CardFilterState — Add Sets Field

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/common/CardFilterState.kt`

- [ ] **Step 1: Add sets to CardFilterState**

Replace the entire file:

```kotlin
package com.github.username.cardapp.ui.common

data class CardFilterState(
    val query: String = "",
    val sets: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val rarities: Set<String> = emptySet(),
    val elements: Set<String> = emptySet(),
    val elementMatchAll: Boolean = false,
    val sort: SortState = SortState(),
    val filtersExpanded: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || sets.isNotEmpty() || types.isNotEmpty() || rarities.isNotEmpty() || elements.isNotEmpty()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/common/CardFilterState.kt
git commit -m "feat: add sets field to CardFilterState"
```

---

### Task 5: Query Builder — Tests First, Then Implementation

**Files:**
- Create: `app/src/main/java/com/github/username/cardapp/data/FilterQueryBuilder.kt`
- Create: `app/src/test/java/com/github/username/cardapp/FilterQueryBuilderTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/github/username/cardapp/FilterQueryBuilderTest.kt
package com.github.username.cardapp

import com.github.username.cardapp.data.FilterQueryBuilder
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterQueryBuilderTest {

    private fun buildSql(state: CardFilterState, collection: Boolean = false): String {
        val query = FilterQueryBuilder.build(state, collection)
        return query.sql
    }

    // --- Base query ---

    @Test
    fun defaultFilterReturnsAllCardsSortedByName() {
        val sql = buildSql(CardFilterState())
        assertTrue(sql.contains("SELECT cards.*"))
        assertTrue(sql.contains("LEFT JOIN prices"))
        assertTrue(sql.contains("ORDER BY cards.name COLLATE NOCASE ASC"))
    }

    @Test
    fun collectionModeScopesToOwnedCards() {
        val sql = buildSql(CardFilterState(), collection = true)
        assertTrue(sql.contains("INNER JOIN collection"))
        assertTrue(sql.contains("collection.quantity"))
    }

    // --- Text search ---

    @Test
    fun textSearchFiltersNameAndRulesText() {
        val sql = buildSql(CardFilterState(query = "dragon"))
        assertTrue(sql.contains("cards.name LIKE ?"))
        assertTrue(sql.contains("cards.rulesText LIKE ?"))
    }

    // --- Set filter ---

    @Test
    fun setFilterUsesLikeWithSentinels() {
        val sql = buildSql(CardFilterState(sets = setOf("Alpha")))
        assertTrue(sql.contains("cards.setNames LIKE ?"))
    }

    @Test
    fun multipleSetFiltersAreOred() {
        val sql = buildSql(CardFilterState(sets = setOf("Alpha", "Beta")))
        // Should have two LIKE clauses ORed
        val setClauseCount = Regex("cards\\.setNames LIKE \\?").findAll(sql).count()
        assertEquals(2, setClauseCount)
    }

    // --- Type filter ---

    @Test
    fun typeFilterUsesIn() {
        val sql = buildSql(CardFilterState(types = setOf("Minion", "Spell")))
        assertTrue(sql.contains("cards.cardType IN ("))
    }

    // --- Rarity filter ---

    @Test
    fun rarityFilterUsesIn() {
        val sql = buildSql(CardFilterState(rarities = setOf("Elite")))
        assertTrue(sql.contains("cards.rarity IN ("))
    }

    // --- Element filter ---

    @Test
    fun elementFilterMatchAny() {
        val sql = buildSql(CardFilterState(elements = setOf("Fire", "Water")))
        assertTrue(sql.contains("cards.fireThreshold > 0"))
        assertTrue(sql.contains(" OR "))
    }

    @Test
    fun elementFilterMatchAll() {
        val sql = buildSql(CardFilterState(elements = setOf("Fire", "Water"), elementMatchAll = true))
        assertTrue(sql.contains("cards.fireThreshold > 0"))
        assertTrue(sql.contains(" AND "))
    }

    @Test
    fun elementNoneFilter() {
        val sql = buildSql(CardFilterState(elements = setOf("None")))
        assertTrue(sql.contains("cards.airThreshold = 0"))
        assertTrue(sql.contains("cards.earthThreshold = 0"))
        assertTrue(sql.contains("cards.fireThreshold = 0"))
        assertTrue(sql.contains("cards.waterThreshold = 0"))
    }

    // --- Sort ---

    @Test
    fun sortByCostAsc() {
        val sort = SortState(name = SortDir.Off, cost = SortDir.Asc, priority = listOf("cost"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("ORDER BY cards.cost ASC"))
    }

    @Test
    fun sortByRarityDesc() {
        val sort = SortState(name = SortDir.Off, rarity = SortDir.Desc, priority = listOf("rarity"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("CASE cards.rarity"))
        assertTrue(sql.contains("DESC"))
    }

    @Test
    fun sortByPriceAsc() {
        val sort = SortState(name = SortDir.Off, price = SortDir.Asc, priority = listOf("price"))
        val sql = buildSql(CardFilterState(sort = sort))
        assertTrue(sql.contains("prices.marketPrice"))
        assertTrue(sql.contains("ASC"))
    }

    @Test
    fun multiFieldSort() {
        val sort = SortState(cost = SortDir.Asc, name = SortDir.Desc, priority = listOf("cost", "name"))
        val sql = buildSql(CardFilterState(sort = sort))
        val costIdx = sql.indexOf("cards.cost ASC")
        val nameIdx = sql.indexOf("cards.name COLLATE NOCASE DESC")
        assertTrue(costIdx < nameIdx)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.github.username.cardapp.FilterQueryBuilderTest" 2>&1 | tail -10
```

Expected: compilation failure — `FilterQueryBuilder` does not exist yet.

- [ ] **Step 3: Implement FilterQueryBuilder**

```kotlin
// app/src/main/java/com/github/username/cardapp/data/FilterQueryBuilder.kt
package com.github.username.cardapp.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir

object FilterQueryBuilder {

    fun build(filter: CardFilterState, collection: Boolean = false): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()

        val select = if (collection) {
            "SELECT cards.*, collection.quantity, prices.marketPrice, prices.lowPrice FROM cards " +
                "INNER JOIN collection ON collection.cardName = cards.name " +
                "LEFT JOIN prices ON prices.cardName = cards.name"
        } else {
            "SELECT cards.*, prices.marketPrice, prices.lowPrice FROM cards " +
                "LEFT JOIN prices ON prices.cardName = cards.name"
        }

        // Text search
        if (filter.query.isNotBlank()) {
            val pattern = "%${filter.query}%"
            where += "(cards.name LIKE ? OR cards.cardType LIKE ? OR cards.subTypes LIKE ? OR cards.rulesText LIKE ?)"
            args += pattern; args += pattern; args += pattern; args += pattern
        }

        // Set filter (match any)
        if (filter.sets.isNotEmpty()) {
            val setClauses = filter.sets.map {
                args += "%,$it,%"
                "cards.setNames LIKE ?"
            }
            where += "(${setClauses.joinToString(" OR ")})"
        }

        // Type filter
        if (filter.types.isNotEmpty()) {
            val placeholders = filter.types.map { "?" }.joinToString(", ")
            where += "cards.cardType IN ($placeholders)"
            args += filter.types.map { it }
        }

        // Rarity filter
        if (filter.rarities.isNotEmpty()) {
            val placeholders = filter.rarities.map { "?" }.joinToString(", ")
            where += "cards.rarity IN ($placeholders)"
            args += filter.rarities.map { it }
        }

        // Element filter
        if (filter.elements.isNotEmpty()) {
            val wantNone = "None" in filter.elements
            val elementKeys = filter.elements - "None"
            val clauses = mutableListOf<String>()

            if (wantNone) {
                clauses += "(cards.airThreshold = 0 AND cards.earthThreshold = 0 AND cards.fireThreshold = 0 AND cards.waterThreshold = 0)"
            }

            for (element in elementKeys) {
                val col = when (element) {
                    "Fire" -> "cards.fireThreshold"
                    "Water" -> "cards.waterThreshold"
                    "Earth" -> "cards.earthThreshold"
                    "Air" -> "cards.airThreshold"
                    else -> continue
                }
                clauses += "$col > 0"
            }

            if (clauses.isNotEmpty()) {
                val joiner = if (filter.elementMatchAll && !wantNone) " AND " else " OR "
                where += "(${clauses.joinToString(joiner)})"
            }
        }

        // Build WHERE clause
        val whereClause = if (where.isNotEmpty()) " WHERE ${where.joinToString(" AND ")}" else ""

        // Build ORDER BY clause
        val orderParts = mutableListOf<String>()
        for (field in filter.sort.priority) {
            val dir = when (field) {
                "name" -> filter.sort.name
                "cost" -> filter.sort.cost
                "rarity" -> filter.sort.rarity
                "price" -> filter.sort.price
                else -> SortDir.Off
            }
            if (dir == SortDir.Off) continue
            val dirStr = if (dir == SortDir.Asc) "ASC" else "DESC"
            when (field) {
                "name" -> orderParts += "cards.name COLLATE NOCASE $dirStr"
                "cost" -> orderParts += "cards.cost $dirStr"
                "rarity" -> orderParts += "CASE cards.rarity WHEN 'Unique' THEN 4 WHEN 'Elite' THEN 3 WHEN 'Exceptional' THEN 2 WHEN 'Ordinary' THEN 1 ELSE 0 END $dirStr"
                "price" -> {
                    val nulls = if (dir == SortDir.Asc) "CASE WHEN prices.marketPrice IS NULL THEN 1 ELSE 0 END ASC" else "CASE WHEN prices.marketPrice IS NULL THEN 1 ELSE 0 END ASC"
                    orderParts += nulls
                    orderParts += "prices.marketPrice $dirStr"
                }
            }
        }
        // Always add name as tiebreaker if not already sorting by name
        if (filter.sort.name == SortDir.Off) {
            orderParts += "cards.name COLLATE NOCASE ASC"
        }

        val orderClause = if (orderParts.isNotEmpty()) " ORDER BY ${orderParts.joinToString(", ")}" else ""

        return SimpleSQLiteQuery("$select$whereClause$orderClause", args.toTypedArray())
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.github.username.cardapp.FilterQueryBuilderTest" 2>&1 | tail -10
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/data/FilterQueryBuilder.kt \
       app/src/test/java/com/github/username/cardapp/FilterQueryBuilderTest.kt
git commit -m "feat: FilterQueryBuilder with full test coverage"
```

---

### Task 6: Repository Layer Update

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/data/CardRepository.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/data/CardRepositoryImpl.kt`

- [ ] **Step 1: Update CardRepository interface**

Replace the full file:

```kotlin
package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.common.CardFilterState
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>>
    fun filteredCollection(filter: CardFilterState): Flow<List<CollectionCardWithPrice>>
    val sets: Flow<List<SetEntity>>
    val priceMap: Flow<Map<String, Double>>
    val totalCardCount: Flow<Int>
    val totalCollectionQuantity: Flow<Int>
    suspend fun needsCardSync(): Boolean
    suspend fun syncCards()
    suspend fun loadPrices()
    suspend fun addToCollection(entries: List<CollectionEntryEntity>)
    suspend fun incrementInCollection(cardName: String)
    suspend fun removeOneFromCollection(cardName: String)
    fun getCardByName(name: String): Flow<CardEntity?>
    fun getVariantsByCardName(cardName: String): Flow<List<CardVariantEntity>>
    suspend fun getFaqs(cardName: String): List<FaqEntry>
    fun ensureDataLoaded()
}
```

- [ ] **Step 2: Update CardRepositoryImpl**

Key changes:
1. Replace `override val cards` with `filteredCards()` and `filteredCollection()`
2. Replace `override val collection` with `filteredCollection()`
3. Replace `_prices` StateFlow with DB-backed `priceMap`
4. Add `sets` flow
5. Add `totalCardCount` and `totalCollectionQuantity` flows
6. Update `syncCards()` to populate `setNames`
7. Update `loadPrices()` to write to DB

Replace the full file:

```kotlin
package com.github.username.cardapp.data

import android.content.Context
import android.util.Log
import com.github.username.cardapp.data.local.CardDao
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.PriceEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.remote.SorceryApi
import com.github.username.cardapp.ui.common.CardFilterState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: CardDao,
    private val api: SorceryApi,
    private val json: Json,
    private val appScope: CoroutineScope,
) : CardRepository {

    override fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>> =
        dao.filteredCards(FilterQueryBuilder.build(filter, collection = false))

    override fun filteredCollection(filter: CardFilterState): Flow<List<CollectionCardWithPrice>> =
        dao.filteredCollectionCards(FilterQueryBuilder.build(filter, collection = true))

    override val sets: Flow<List<SetEntity>> = dao.getAllSets()

    override val priceMap: Flow<Map<String, Double>> = dao.getAllPrices()
        .map { list -> list.mapNotNull { p -> p.marketPrice?.let { p.cardName to it } }.toMap() }

    override val totalCardCount: Flow<Int> = dao.getAllCards().map { it.size }

    override val totalCollectionQuantity: Flow<Int> = dao.getCollectionEntries()
        .map { entries -> entries.sumOf { it.quantity } }

    private val ensureDataOnce by lazy {
        appScope.launch {
            if (needsCardSync()) syncCards()
            loadPrices()
        }
    }

    override fun ensureDataLoaded() {
        ensureDataOnce
    }

    override suspend fun needsCardSync(): Boolean = dao.getCardCount() == 0

    override suspend fun addToCollection(entries: List<CollectionEntryEntity>) {
        dao.upsertCollectionEntries(entries)
    }

    override suspend fun incrementInCollection(cardName: String) {
        dao.incrementCollectionEntry(cardName)
    }

    override suspend fun removeOneFromCollection(cardName: String) {
        dao.removeOneFromCollection(cardName)
    }

    override fun getCardByName(name: String) = dao.getCardByName(name)

    override fun getVariantsByCardName(cardName: String) = dao.getVariantsByCardName(cardName)

    private var faqCache: Map<String, List<FaqEntry>>? = null
    private val faqMutex = Mutex()

    override suspend fun getFaqs(cardName: String): List<FaqEntry> = withContext(Dispatchers.IO) {
        val cache = faqMutex.withLock {
            faqCache ?: try {
                val raw = context.assets.open("faqs.json").bufferedReader().use { it.readText() }
                val map = json.decodeFromString<Map<String, List<FaqEntry>>>(raw)
                faqCache = map
                map
            } catch (e: Exception) {
                Log.w("CardRepository", "Failed to load FAQs", e)
                emptyMap()
            }
        }
        cache[cardName].orEmpty()
    }

    override suspend fun loadPrices() = withContext(Dispatchers.IO) {
        try {
            val raw = context.assets.open("prices.json").bufferedReader().use { it.readText() }
            val data = json.decodeFromString<PricesJson>(raw)
            val map = mutableMapOf<String, PriceEntity>()
            val promoSets = setOf("dust reward promos", "arthurian legends promo")
            for (entry in data.cards) {
                val name = entry.productName ?: continue
                val market = entry.marketPrice ?: continue
                val isPromo = entry.setName?.lowercase() in promoSets
                val existing = map[name]
                if (existing == null ||
                    (existing.marketPrice != null && isPromo.not() && market < existing.marketPrice) ||
                    existing.marketPrice == null
                ) {
                    map[name] = PriceEntity(
                        cardName = name,
                        marketPrice = market,
                        lowPrice = entry.lowestPrice ?: market,
                    )
                }
            }
            dao.upsertPrices(map.values.toList())
        } catch (e: Exception) {
            Log.w("CardRepository", "Failed to load prices", e)
        }
    }

    private suspend fun fetchCardList(): List<CardJson> {
        return try {
            val remote = api.getCards()
            Log.d("CardRepository", "Fetched ${remote.size} cards from API")
            remote
        } catch (e: Exception) {
            Log.d("CardRepository", "API unavailable, using bundled data: ${e.message}")
            val raw = context.assets.open("cards.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<CardJson>>(raw)
        }
    }

    override suspend fun syncCards() = withContext(Dispatchers.IO) {
        val cardList = fetchCardList()

        val setMap = linkedMapOf<String, String>()
        for (card in cardList) {
            for (set in card.sets) {
                val name = set.name ?: continue
                if (name !in setMap) setMap[name] = set.releasedAt.orEmpty()
            }
        }
        val setEntities = setMap.entries
            .sortedBy { it.value }
            .mapIndexed { index, (name, releasedAt) ->
                SetEntity(name = name, releasedAt = releasedAt, releaseOrder = index + 1)
            }
        val setOrder = setEntities.associate { it.name to it.releaseOrder }

        val primarySlugOverrides = mapOf(
            "Apprentice Wizard" to "pro-apprentice_wizard-wk-s",
            "Grandmaster Wizard" to "pro-grandmaster_wizard-wk-s",
        )

        // Build setNames lookup: cardName → ",Alpha,Beta,"
        val cardSetNames = mutableMapOf<String, MutableSet<String>>()
        for (card in cardList) {
            for (set in card.sets) {
                val setName = set.name ?: continue
                cardSetNames.getOrPut(card.name) { mutableSetOf() }.add(setName)
            }
        }

        val cardEntities = cardList.map { card ->
            val g = card.guardian
            val primarySlug = primarySlugOverrides[card.name]
                ?: card.sets
                    .sortedBy { setOrder[it.name] ?: Int.MAX_VALUE }
                    .firstNotNullOfOrNull { set ->
                        set.variants.firstOrNull { it.finish.equals("Standard", ignoreCase = true) }?.slug
                            ?: set.variants.firstOrNull()?.slug
                    }
                    .orEmpty()

            val setsForCard = cardSetNames[card.name]
            val setNamesStr = if (setsForCard.isNullOrEmpty()) "" else ",${setsForCard.joinToString(",")},";

            CardEntity(
                name = card.name,
                primarySlug = primarySlug,
                elements = card.elements.orEmpty(),
                subTypes = card.subTypes.orEmpty(),
                cardType = g?.type.orEmpty(),
                rarity = g?.rarity.orEmpty(),
                cost = g?.cost ?: 0,
                attack = g?.attack ?: 0,
                defence = g?.defence ?: 0,
                life = g?.life,
                rulesText = g?.rulesText.orEmpty(),
                airThreshold = g?.thresholds?.air ?: 0,
                earthThreshold = g?.thresholds?.earth ?: 0,
                fireThreshold = g?.thresholds?.fire ?: 0,
                waterThreshold = g?.thresholds?.water ?: 0,
                setNames = setNamesStr,
            )
        }

        val variantEntities = cardList.flatMap { card ->
            card.sets.flatMap { set ->
                set.variants.map { variant ->
                    CardVariantEntity(
                        slug = variant.slug,
                        cardName = card.name,
                        setName = set.name.orEmpty(),
                        finish = variant.finish.orEmpty(),
                        product = variant.product.orEmpty(),
                        artist = variant.artist.orEmpty(),
                        flavorText = variant.flavorText.orEmpty(),
                        typeText = variant.typeText.orEmpty(),
                    )
                }
            }
        }

        dao.syncAll(setEntities, cardEntities, variantEntities)
    }
}
```

- [ ] **Step 3: Update CardDao.getCardCount to be non-suspend if needed**

Check if `totalCardCount` flow works — `getCardCount()` is `suspend`, but we need a `Flow<Int>`. Since we already use `getAllCards().map { it.size }` above, that works. If preferred, add a dedicated query:

```kotlin
@Query("SELECT COUNT(*) FROM cards")
fun observeCardCount(): Flow<Int>
```

Then use it in the repository instead of `getAllCards().map { it.size }`.

- [ ] **Step 4: Verify compilation**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Note: this will fail until ViewModels are updated in the next tasks. The repository interface changed, so callers need updating. That's expected — proceed to the next tasks.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/data/CardRepository.kt \
       app/src/main/java/com/github/username/cardapp/data/CardRepositoryImpl.kt
git commit -m "feat: repository — DB-backed filtering, prices, and sets"
```

---

### Task 7: CardsViewModel Update

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/cards/CardsViewModel.kt`

- [ ] **Step 1: Rewrite CardsViewModel**

Replace the full file:

```kotlin
package com.github.username.cardapp.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CardWithPrice
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.common.CardFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CardRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val cards: StateFlow<List<CardWithPrice>> = _filterState
        .flatMapLatest { filter -> repository.filteredCards(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCardCount: StateFlow<Int> = repository.totalCardCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sets: StateFlow<List<SetEntity>> = repository.sets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collectedQuantities: StateFlow<Map<String, Int>> = repository.filteredCollection(CardFilterState())
        .map { entries -> entries.associate { it.card.name to it.quantity } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        repository.ensureDataLoaded()
        viewModelScope.launch {
            val savedSort = sortPreferences.sortState.first()
            _filterState.value = _filterState.value.copy(sort = savedSort)
        }
    }

    fun updateFilter(transform: (CardFilterState) -> CardFilterState) {
        val old = _filterState.value
        val new = transform(old)
        _filterState.value = new
        if (old.sort != new.sort) {
            viewModelScope.launch { sortPreferences.save(new.sort) }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/cards/CardsViewModel.kt
git commit -m "feat: CardsViewModel — flatMapLatest DB filtering, expose sets"
```

---

### Task 8: CollectionViewModel Update

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/collection/CollectionViewModel.kt`

- [ ] **Step 1: Rewrite CollectionViewModel**

Replace the full file:

```kotlin
package com.github.username.cardapp.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.ui.common.CardFilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CardRepository,
    private val sortPreferences: SortPreferences,
) : ViewModel() {

    private val _filterState = MutableStateFlow(CardFilterState())
    val filterState: StateFlow<CardFilterState> = _filterState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<CollectionCardWithPrice>> = _filterState
        .flatMapLatest { filter -> repository.filteredCollection(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCardCount: StateFlow<Int> = repository.totalCollectionQuantity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sets: StateFlow<List<SetEntity>> = repository.sets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        repository.ensureDataLoaded()
        viewModelScope.launch {
            val savedSort = sortPreferences.sortState.first()
            _filterState.value = _filterState.value.copy(sort = savedSort)
        }
    }

    fun increment(cardName: String) {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }

    fun decrement(cardName: String) {
        viewModelScope.launch { repository.removeOneFromCollection(cardName) }
    }

    fun importCollection(text: String) {
        viewModelScope.launch {
            val entries = text.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val spaceIndex = line.indexOf(' ')
                    if (spaceIndex < 1) return@mapNotNull null
                    val qty = line.substring(0, spaceIndex).toIntOrNull() ?: return@mapNotNull null
                    val name = line.substring(spaceIndex + 1).trim()
                    if (name.isEmpty() || qty <= 0) return@mapNotNull null
                    CollectionEntryEntity(cardName = name, quantity = qty)
                }
            if (entries.isNotEmpty()) repository.addToCollection(entries)
        }
    }

    fun updateFilter(transform: (CardFilterState) -> CardFilterState) {
        val old = _filterState.value
        val new = transform(old)
        _filterState.value = new
        if (old.sort != new.sort) {
            viewModelScope.launch { sortPreferences.save(new.sort) }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/collection/CollectionViewModel.kt
git commit -m "feat: CollectionViewModel — flatMapLatest DB filtering, expose sets"
```

---

### Task 9: ScanViewModel Price Compatibility

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/scan/ScanViewModel.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/ui/scan/ScanScreen.kt`

The ScanScreen currently uses `prices: Map<String, PriceInfo>` for display. With the repository change, prices are now `Flow<Map<String, Double>>`.

- [ ] **Step 1: Update ScanViewModel prices**

In `ScanViewModel.kt`, change the `prices` property from:

```kotlin
val prices: StateFlow<Map<String, PriceInfo>> = repository.prices
```

To:

```kotlin
val prices: StateFlow<Map<String, Double>> = repository.priceMap
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
```

Add import:

```kotlin
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
```

(Check if these imports already exist — they may.)

- [ ] **Step 2: Update ScanScreen price references**

In `ScanScreen.kt`, find `val prices by vm.prices.collectAsState()` and update the type used downstream:

The `prices` variable changes from `Map<String, PriceInfo>` to `Map<String, Double>`.

Find all references to `prices[...].marketPrice` or `prices[...]?.marketPrice` in ScanScreen.kt and change them to just `prices[...]`:

```
// Before:
marketPrice = prices[entry.card.name]?.marketPrice,
// After:
marketPrice = prices[entry.card.name],
```

Also update `ScannedCardsPanel` parameter type from `prices: Map<String, PriceInfo>` to `prices: Map<String, Double>` and its internal references.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/scan/ScanViewModel.kt \
       app/src/main/java/com/github/username/cardapp/ui/scan/ScanScreen.kt
git commit -m "refactor: ScanScreen — use DB-backed price map"
```

---

### Task 10: SearchFilterBar — Set Filter Chips

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/common/SearchFilterBar.kt`

- [ ] **Step 1: Add availableSets parameter to SearchFilterBar**

Change the `SearchFilterBar` signature to accept the set list:

```kotlin
@Composable
fun SearchFilterBar(
    state: CardFilterState,
    onUpdate: (CardFilterState) -> Unit,
    availableSets: List<String> = emptyList(),
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Update filterActive check**

Change the `filterActive` variable (currently line 137) to include sets:

```kotlin
val filterActive = state.sets.isNotEmpty() || state.types.isNotEmpty() || state.rarities.isNotEmpty() || state.elements.isNotEmpty()
```

- [ ] **Step 3: Add set chips to ActiveFilterSummary**

In `ActiveFilterSummary`, add set dismiss chips before the type chips:

```kotlin
for (s in state.sets) {
    DismissChip(label = s) {
        onUpdate(state.copy(sets = state.sets - s))
    }
}
```

Update the "CLEAR ALL" chip to also clear sets:

```kotlin
DismissChip(label = "CLEAR ALL") {
    onUpdate(state.copy(sets = emptySet(), types = emptySet(), rarities = emptySet(), elements = emptySet(), query = ""))
}
```

- [ ] **Step 4: Add SET section to FilterPanel**

Pass `availableSets` into `FilterPanel`. Update its signature:

```kotlin
@Composable
private fun FilterPanel(
    state: CardFilterState,
    onUpdate: (CardFilterState) -> Unit,
    availableSets: List<String>,
)
```

Add the SET section at the top of FilterPanel's Column, before the TYPE section:

```kotlin
// Set row
if (availableSets.isNotEmpty()) {
    FilterSectionLabel("SET")
    Spacer(Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (setName in availableSets) {
            FilterChip(
                label = setName.uppercase(),
                selected = setName in state.sets,
                onClick = {
                    val newSets = if (setName in state.sets) state.sets - setName else state.sets + setName
                    onUpdate(state.copy(sets = newSets))
                },
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
```

- [ ] **Step 5: Update FilterPanel call site**

In `SearchFilterBar`, update the `FilterPanel` call to pass `availableSets`:

```kotlin
FilterPanel(state = state, onUpdate = onUpdate, availableSets = availableSets)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/common/SearchFilterBar.kt
git commit -m "feat: SearchFilterBar — add set filter chips"
```

---

### Task 11: Screen Updates — CardsScreen + CollectionScreen

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/cards/CardsScreen.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/ui/collection/CollectionScreen.kt`

- [ ] **Step 1: Update CardsScreen**

In `CardsScreen`, the ViewModel now exposes `cards: StateFlow<List<CardWithPrice>>` instead of `StateFlow<List<CardEntity>>`, and `sets`.

Add `sets` collection:

```kotlin
val sets by vm.sets.collectAsState()
```

Pass `availableSets` to `SearchFilterBar`:

```kotlin
SearchFilterBar(
    state = filterState,
    onUpdate = { newState -> vm.updateFilter { newState } },
    availableSets = sets.map { it.name },
)
```

Update usages of `cards` — each element is now `CardWithPrice` instead of `CardEntity`. Wherever the screen passes a card to a composable or reads card properties, use `.card` to access the embedded entity. For example, if `CardGrid` takes `List<CardEntity>`, change it to accept `List<CardWithPrice>`, or map: `cards.map { it.card }`.

Check `CardGrid` and `CardTile` — if they take `CardEntity`, pass `cardWithPrice.card`. If they need price, pass `cardWithPrice.marketPrice`.

- [ ] **Step 2: Update CollectionScreen**

In `CollectionScreen`, the ViewModel now exposes `entries: StateFlow<List<CollectionCardWithPrice>>` instead of `List<CollectionCardRow>`.

Add `sets` collection:

```kotlin
val sets by vm.sets.collectAsState()
```

Pass `availableSets` to `SearchFilterBar`:

```kotlin
SearchFilterBar(
    state = filterState,
    onUpdate = { newState -> vm.updateFilter { newState } },
    availableSets = sets.map { it.name },
)
```

Update price references. Replace:

```kotlin
val price = prices[entry.card.name]?.marketPrice ?: 0.0
```

With:

```kotlin
val price = entry.marketPrice ?: 0.0
```

Update `CardRow` calls from `marketPrice = prices[entry.card.name]?.marketPrice` to `marketPrice = entry.marketPrice`.

Remove `val prices by vm.prices.collectAsState()` — no longer needed.

- [ ] **Step 3: Verify compilation**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/cards/CardsScreen.kt \
       app/src/main/java/com/github/username/cardapp/ui/collection/CollectionScreen.kt
git commit -m "feat: screens — pass sets to filter bar, use CardWithPrice for prices"
```

---

### Task 12: Cleanup — Remove applyFilter and Update Tests

**Files:**
- Delete contents of: `app/src/main/java/com/github/username/cardapp/ui/common/CardFilter.kt`
- Modify: `app/src/test/java/com/github/username/cardapp/CardFilterTest.kt`

- [ ] **Step 1: Delete applyFilter()**

Replace `CardFilter.kt` with an empty file (keep the package declaration):

```kotlin
package com.github.username.cardapp.ui.common
```

(The file can be fully deleted if preferred, but keeping the package avoids breaking anything that might import from it. Verify no remaining imports reference `applyFilter`.)

- [ ] **Step 2: Remove applyFilter imports**

Search for any remaining `import com.github.username.cardapp.ui.common.applyFilter` across the codebase and remove them:

```bash
grep -rn "import.*applyFilter" app/src/main/java/
```

Remove each occurrence found.

- [ ] **Step 3: Update CardFilterTest**

The existing `CardFilterTest` tests `applyFilter()` which no longer exists. Remove those tests. The `SortStateToggleTest` class in the same file is still valid — keep it. Also remove the `applyFilter` import:

```kotlin
package com.github.username.cardapp

import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import com.github.username.cardapp.ui.common.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortStateToggleTest {

    @Test
    fun toggleOffToAsc() {
        val state = SortState()
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Asc, toggled.cost)
        assertTrue(toggled.priority.contains("cost"))
    }

    @Test
    fun toggleAscToDesc() {
        val state = SortState(cost = SortDir.Asc, priority = listOf("name", "cost"))
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Desc, toggled.cost)
    }

    @Test
    fun toggleDescToOff() {
        val state = SortState(cost = SortDir.Desc, priority = listOf("name", "cost"))
        val toggled = state.toggle("cost")
        assertEquals(SortDir.Off, toggled.cost)
        assertTrue(!toggled.priority.contains("cost"))
    }

    @Test
    fun toggleMovesToEndOfPriority() {
        val state = SortState(
            name = SortDir.Asc,
            cost = SortDir.Asc,
            priority = listOf("cost", "name"),
        )
        val toggled = state.toggle("cost")
        assertEquals(listOf("name", "cost"), toggled.priority)
    }

    @Test
    fun toggleUnknownFieldReturnsUnchanged() {
        val state = SortState()
        val toggled = state.toggle("unknown")
        assertEquals(state, toggled)
    }
}
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew test 2>&1 | tail -10
```

Expected: all tests PASS.

- [ ] **Step 5: Run full build**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/common/CardFilter.kt \
       app/src/test/java/com/github/username/cardapp/CardFilterTest.kt
git commit -m "refactor: remove in-memory applyFilter, update tests"
```
