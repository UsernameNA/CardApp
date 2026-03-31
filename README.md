# CardApp

<img src="app/src/main/ic_launcher-playstore.png" width="512" alt="CardApp icon">

A personal collection manager for **Sorcery: Contested Realm** trading cards, built for Android with Jetpack Compose.

## Features

- **Card catalogue** — browse all cards with rarity indicators, type info, and market prices
- **Collection tracking** — manage your owned cards with quantity controls
- **Card scanning** — point your camera at a physical card for OCR-based identification; manual and auto-scan modes with frame fingerprinting
- **Search and filtering** — text search across name, type, subtype, and rules text; filter by card type, rarity, and element; AND/OR element matching; sort by name, cost, rarity, or price
- **Market prices** — TCGPlayer market prices displayed inline; sortable ascending/descending
- **Offline-first** — card data, images, and prices are bundled with the app; no internet required after setup

## Screenshots

| Landing         | Collection      |
|-----------------|-----------------|
| *(coming soon)* | *(coming soon)* |

## Tech Stack

- **Language**: Kotlin 2.3
- **UI**: Jetpack Compose + Material 3
- **Serialization**: Kotlin Serialization
- **Architecture**: single-module, MVVM (Hilt + ViewModel + StateFlow)
- **Local storage**: Room (SQLite)
- **Images**: Coil
- **Camera**: CameraX + ML Kit Text Recognition
- **Build**: R8 minification + resource shrinking enabled
- **Min SDK**: 36 (Android 15)

## Installation

Download the latest APK from the [Releases](https://github.com/UsernameNA/CardApp/releases) page and install it on your Android 15+ device.

## Building

```bash
./gradlew assembleDebug      # debug APK (small image subset, fast)
./gradlew assembleRelease    # release APK (all card images included)
./gradlew test               # unit tests
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
Release APK: `app/build/outputs/apk/release/app-release.apk`

Release builds automatically copy the full image set from `scripts/images/` into the APK. Debug builds use a small subset from `app/src/main/assets/images/` for faster iteration.

## Data Setup

Card data needs to be fetched before building. Images are stored in `scripts/images/` (not committed due to size).

### 1. Fetch card data

```bash
python3 scripts/fetch_card_data.py
```

Downloads the full card catalogue from the Sorcery TCG API and writes it to `app/src/main/assets/cards.json`.

### 2. Fetch market prices

```bash
pip3 install requests
python3 scripts/fetch_prices.py
```

Fetches current market prices from TCGPlayer for all Sorcery sets and writes them to `app/src/main/assets/prices.json`. Run periodically to keep prices up to date.

### 3. Convert card images

```bash
pip3 install Pillow
python3 scripts/convert_images.py [INPUT_DIR] [QUALITY]
```

Converts PNG card images to WebP for efficient bundling. Defaults to `scripts/cards_png/` as input and quality `80`. Output goes to `scripts/images/`.

## Project Structure

```
app/src/main/java/com/github/usernamena/cardapp/
├── CardAppApplication.kt
├── MainActivity.kt
├── di/                 # Hilt dependency injection modules
├── data/
│   ├── CardRepository.kt
│   ├── local/          # Room database, DAO, entities
│   └── model/          # JSON deserialization models
└── ui/
    ├── cards/          # Card catalogue grid + ViewModel
    ├── collection/     # Collection list + ViewModel
    ├── common/         # Shared components (CardRow, SearchFilterBar, CardFilter)
    ├── landing/        # Landing screen
    ├── scan/           # Camera scan screen + ViewModel
    └── theme/          # Colour palette, typography, modifiers
scripts/
├── fetch_card_data.py  # Downloads cards.json from API
├── fetch_prices.py     # Fetches market prices from TCGPlayer
├── convert_images.py   # PNG → WebP conversion via Pillow
└── images/             # Full card image set (used by release builds)
```

## Roadmap

- [x] Landing screen
- [x] Card catalogue browser
- [x] Card scanning with camera (CameraX + ML Kit OCR)
- [x] Collection tracking with quantity management
- [x] Search and filtering
- [x] Market prices from TCGPlayer
- [x] Dependency injection (Hilt)
- [x] Card detail view
- [ ] Deck builder

## Architecture TODO

Gaps to address as a learning exercise:

- [x] **Repository interface** — `CardRepository` interface with `CardRepositoryImpl`; bound via Hilt `@Binds`
- [ ] **Error handling** — propagate errors via `Result<T>` or a sealed class instead of swallowing exceptions; add user-facing error/retry UI
- [x] **Type-safe navigation** — `@Serializable` route objects replace hardcoded strings; uses `composable<Route>` and `navigate(Route)`
- [x] **Persist user preferences (DataStore)** — sort order persists across app restarts via Jetpack DataStore
- [ ] **Retrofit** — add a remote data source for fetching card data and prices from APIs instead of bundling JSON assets
- [ ] **Paging 3** — paginate the card catalogue grid and collection list for efficient loading of large datasets
- [ ] **WorkManager** — schedule periodic background price sync so prices stay fresh without manual rebuilds
- [x] **ProGuard/R8** — enable `isMinifyEnabled` for release builds to shrink APK size and obfuscate code
- [x] **Kotlin Serialization** — replace Gson with compile-time serialization (no reflection, no ProGuard keep rules)
- [ ] **Unit tests** — ViewModel and Repository tests with mocked dependencies (Hilt makes this practical now)
- [ ] **UI tests** — Compose UI tests for key screens using `createComposeRule()`
