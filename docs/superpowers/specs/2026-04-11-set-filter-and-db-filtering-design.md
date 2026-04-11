# Set Filter & Database-Level Filtering

## Summary

Add a "filter by set" option to the Cards and Collection screens, and migrate the entire filtering pipeline from in-memory Kotlin to Room SQL queries. Prices move from an in-memory StateFlow to a database table so that price sorting also happens in Room.

## Decisions

- **Set filter semantics:** match-any (card passes if any of its set names intersects with selected sets)
- **Set list:** dynamic from the `sets` table, ordered by `releaseOrder`
- **Filter panel order:** Set (new) -> Type -> Rarity -> Element
- **Set data on CardEntity:** denormalized `setNames` comma-separated column, avoids JOIN on every filter query
- **Filtering location:** all filtering and sorting moves to Room via a dynamic query builder
- **Prices:** new `PriceEntity` table replaces the in-memory `StateFlow<Map<String, PriceInfo>>`; price sorting uses a LEFT JOIN

## Data Layer

### CardEntity changes

Add column:

```
setNames: String  // comma-delimited with sentinels, e.g. ",Alpha,Beta,"
```

Populated during `syncCards()` by collecting distinct `setName` values from that card's variants, joining them with commas, and wrapping with leading/trailing commas for safe LIKE matching (prevents "Alpha" from matching "Alpha Extended").

### New PriceEntity table

```kotlin
@Entity(tableName = "prices")
data class PriceEntity(
    @PrimaryKey val cardName: String,  // FK to cards.name
    val marketPrice: Double?,
    val lowPrice: Double?,
)
```

Populated via `syncPrices()` upsert when API prices are fetched. Replaces the in-memory `StateFlow<Map<String, PriceInfo>>`.

### Room migration

- Add `setNames TEXT NOT NULL DEFAULT ''` column to `cards` table
- Create `prices` table
- Run one-time UPDATE to populate `setNames` from the variants table:
  ```sql
  UPDATE cards SET setNames = (
      SELECT ',' || GROUP_CONCAT(DISTINCT setName) || ',' FROM variants WHERE variants.cardName = cards.name
  )
  ```

### New indexes

- `cards` table: `cardType`, `rarity`
- `variants` table: `cardName`, `setName`
- `prices` table: indexed by PK (`cardName`)

### CardDao changes

Replace `getAllCards(): Flow<List<CardEntity>>` with a dynamic query method:

```kotlin
@RawQuery(observedEntities = [CardEntity::class, PriceEntity::class])
fun filteredCards(query: SupportSQLiteQuery): Flow<List<CardWithPrice>>
```

Add `getAllSets(): Flow<List<SetEntity>>` exposure (already exists in DAO).

### CardWithPrice result type

Query results include price data alongside card data:

```kotlin
data class CardWithPrice(
    @Embedded val card: CardEntity,
    val marketPrice: Double?,
    val lowPrice: Double?,
)
```

Returned by the dynamic query via `SELECT cards.*, prices.marketPrice, prices.lowPrice FROM cards LEFT JOIN prices ON prices.cardName = cards.name`.

## Repository Layer

### CardRepository interface changes

Remove:
- `val cards: Flow<List<CardEntity>>`
- `val prices: StateFlow<Map<String, PriceInfo>>`

Add:
- `fun filteredCards(filter: CardFilterState): Flow<List<CardWithPrice>>`
- `val sets: Flow<List<SetEntity>>`

### Query builder

A pure function: `buildFilterQuery(filter: CardFilterState): SupportSQLiteQuery`

Builds SQL from `CardFilterState`:

| Filter | SQL clause |
|--------|-----------|
| Text search | `WHERE (name LIKE '%q%' OR cardType LIKE '%q%' OR subTypes LIKE '%q%' OR rulesText LIKE '%q%')` |
| Set | `WHERE (setNames LIKE '%,Alpha,%' OR setNames LIKE '%,Beta,%')` (values stored with leading/trailing commas: `,Alpha,Beta,`) |
| Type | `WHERE cardType IN ('Minion', 'Spell')` |
| Rarity | `WHERE rarity IN ('Elite', 'Unique')` |
| Element (match any) | `WHERE (fireThreshold > 0 OR waterThreshold > 0)` |
| Element (match all) | `WHERE (fireThreshold > 0 AND waterThreshold > 0)` |
| Element "None" | `WHERE (airThreshold = 0 AND earthThreshold = 0 AND fireThreshold = 0 AND waterThreshold = 0)` |
| Sort by name | `ORDER BY name ASC/DESC` |
| Sort by cost | `ORDER BY cost ASC/DESC` |
| Sort by rarity | `ORDER BY CASE rarity WHEN 'Unique' THEN 4 ... END ASC/DESC` |
| Sort by price | `ORDER BY prices.marketPrice ASC/DESC NULLS LAST` |

Base query: `SELECT cards.*, prices.marketPrice, prices.lowPrice FROM cards LEFT JOIN prices ON prices.cardName = cards.name`

All WHERE clauses are ANDed together. Empty filter selections are omitted (no clause = show all).

### applyFilter() removal

`CardFilter.kt`'s `applyFilter()` extension function is deleted. All filtering logic moves into the query builder.

## ViewModel Layer

### CardsViewModel

Replace:
```kotlin
val cards = combine(allCards, _filterState, prices) { cards, filter, priceMap ->
    cards.applyFilter(filter, priceMap)
}
```

With:
```kotlin
val cards: StateFlow<List<CardWithPrice>> = _filterState
    .flatMapLatest { filter -> repository.filteredCards(filter) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

val sets: StateFlow<List<SetEntity>> = repository.sets
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

`updateFilter()` stays the same. The `_filterState` change triggers `flatMapLatest` which re-queries the DB automatically.

### CollectionViewModel

Same pattern. The query builder adds `WHERE name IN (SELECT cardName FROM collection)` to scope results to owned cards.

## UI Layer

### CardFilterState

Add:
```kotlin
val sets: Set<String> = emptySet()
```

### SearchFilterBar.kt

New "SET" section at the top of the filter panel (above Type):
- Receives `availableSets: List<SetEntity>` parameter
- Renders `FilterChip` per set, ordered by `releaseOrder`
- Selected sets appear as `DismissChip`s in `ActiveFilterSummary`
- Same Arcane Codex styling as existing filter sections

### CardsScreen / CollectionScreen

- Pass `sets` from ViewModel into `SearchFilterBar`
- Switch from `prices[card.name]?.marketPrice` to reading `cardWithPrice.marketPrice` directly

### No other visual changes

Filter expand/collapse, dismiss-chip interactions, and all Arcane Codex styling remain identical.

## Scope exclusions

- No full-text search (FTS) — LIKE queries are sufficient at current and near-future scale
- No filter persistence beyond sort preferences (existing behavior)
- No changes to ScanScreen filtering (uses its own search mechanism)
