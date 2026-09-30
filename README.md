# CardApp

<img src="app/src/main/ic_launcher-playstore.png" width="512" alt="CardApp icon">

A personal collection manager for **Sorcery: Contested Realm** trading cards, built for Android with Jetpack Compose.

## Features

- **Card catalogue** — browse all cards with rarity indicators, type info, and market prices
- **Collection tracking** — manage your owned cards with quantity controls
- **Card scanning** — point your camera at a physical card for OCR-based identification; manual and auto-scan modes with frame fingerprinting. Each scan reads the card over several frames and merges the scores, and a match that only narrowly beats the runner-up is sent to the correction panel rather than accepted
- **Card FAQs** — official rulings from curiosa.io shown on the card detail screen
- **Search and filtering** — text search across name, type, subtype, and rules text; filter by card type, rarity, and element; AND/OR element matching; sort by name, cost, rarity, or price
- **Market prices** — TCGPlayer market prices displayed inline; sortable ascending/descending
- **Fully offline** — the app has no internet permission; card data, images, and prices ship inside the APK and are refreshed by app updates

## Screenshots

| Landing         | Collection      |
|-----------------|-----------------|
| *(coming soon)* | *(coming soon)* |

## Tech Stack

- **Language**: Kotlin 2.4
- **UI**: Jetpack Compose + Material 3
- **Serialization**: Kotlin Serialization
- **Architecture**: single-module, MVVM (Hilt + ViewModel + StateFlow)
- **Local storage**: Room (SQLite)
- **Images**: Coil
- **Camera**: CameraX + ML Kit Text Recognition
- **Build**: R8 minification + resource shrinking enabled
- **Min SDK**: 36 (Android 16), target SDK 37

## Installation

Download the latest APK from the [Releases](https://github.com/UsernameNA/CardApp/releases) page and install it on your Android 16+ device.

Releases are around 340MB because the full card art ships inside the APK. A release that is only tens of MB is missing its images — see [Releasing](#releasing).

## Building

```bash
./gradlew assembleDebug      # debug APK (small image subset, fast)
./gradlew assembleRelease    # release APK (all card images included)
./gradlew test               # unit tests
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
Release APK: `app/build/outputs/apk/release/app-release.apk`

Release builds automatically copy the full image set from `scripts/images/` into the APK. Debug builds use a small subset from `app/src/main/assets/images/` for faster iteration.

## Releasing

**Release artifacts must be built locally.** `scripts/images/` is gitignored (~293MB), so a CI checkout has nothing for `copyReleaseImages` to bundle. v1.4.0 and v1.4.1 shipped that way — every card blank, 56MB against v1.3.0's 341MB — so the task now fails when it finds fewer than 3,000 images. `-PallowMissingReleaseImages=true` waives the check for builds that will never be published, such as the CI smoke test.

The `Release APK` workflow is therefore `workflow_dispatch`-only, waives the image check, and uploads to a build artifact instead of publishing. The `CI` workflow runs the unit tests on every push and pull request. Signing needs `keystore.properties` and `release.keystore` in the repo root (both gitignored); CI falls back to `KEYSTORE_*` environment variables.

```bash
# 1. bump versionCode and versionName in app/build.gradle.kts
./gradlew test assembleRelease

# 2. sanity-check the artifact
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c 'assets/images/.*webp'   # expect ~3000
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk              # must match the previous release

# 3. tag and publish
git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
gh release create vX.Y.Z --title "vX.Y.Z" --notes-file notes.md \
  app/build/outputs/apk/release/app-release.apk
```

The certificate check matters: a mismatch is silent until a user tries to update in place and is told to uninstall first.

## Data Setup

Card data needs to be fetched before building. Images are stored in `scripts/images/` (not committed due to size).

To refresh everything:

```bash
pip3 install requests
python3 scripts/fetch_card_data.py   # -> app/src/main/assets/cards.json
python3 scripts/fetch_faqs.py        # -> app/src/main/assets/faqs.json
python3 scripts/fetch_prices.py      # -> app/src/main/assets/prices.json
```

### 1. Fetch card data

```bash
python3 scripts/fetch_card_data.py
```

Downloads the full card catalogue from the Sorcery TCG API and writes it to `app/src/main/assets/cards.json`. Stdlib only, no API key.

Variant slugs are the filenames the app looks up in `assets/images/`, so a change to their format upstream will break every card image until the art is renamed to match.

The app re-imports its database copy whenever `cards.json`, `prices.json`, or the set of bundled images differs from what it last imported, so an app update carries new data to existing installs. The collection survives re-imports; entries whose card leaves the catalogue are kept, hidden, and reappear if it returns. A card's display image is its earliest standard print that has bundled art.

### 2. Fetch card FAQs

```bash
python3 scripts/fetch_faqs.py
```

Scrapes official rulings from curiosa.io and writes them to `app/src/main/assets/faqs.json`, grouped by card name. Stdlib only.

This one parses the site's `__NEXT_DATA__` blob, so it is the most fragile of the three — it exits with an error rather than writing a truncated file if the page structure changes.

### 3. Fetch market prices

```bash
pip3 install requests
python3 scripts/fetch_prices.py
```

Fetches current market prices from TCGPlayer for all Sorcery sets and writes them to `app/src/main/assets/prices.json`. Run periodically to keep prices up to date. Requires `requests` — the only third-party dependency across the three fetchers.

Note that "Arthurian Legends" and "Arthurian Legends Promo" are distinct sets in this data and must not be merged.

### 4. Convert card images

```bash
pip3 install Pillow
python3 scripts/convert_images.py [INPUT_DIR] [QUALITY]
```

Converts PNG card images to WebP for efficient bundling. Defaults to `scripts/cards_png/` as input and quality `80`. Output goes to `scripts/images/`.

Source PNGs must be named after the variant slug (`001-abundance-b-s.png`), since the converted filename is what the app resolves at runtime.

## Project Structure

```
app/src/main/java/com/github/username/cardapp/
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
    ├── detail/         # Card detail screen (variants, prices, FAQs)
    ├── landing/        # Landing screen + dice roller
    ├── scan/           # Camera scan screen, OCR scorer, frame burst
    ├── theme/          # Colour palette, typography, modifiers
    └── trackgame/      # Life/turn tracker
scripts/
├── fetch_card_data.py  # Downloads cards.json from the Sorcery TCG API
├── fetch_faqs.py       # Scrapes card rulings from curiosa.io
├── fetch_prices.py     # Fetches market prices from TCGPlayer
├── convert_images.py   # PNG → WebP conversion via Pillow
├── cards_png/          # Source card art (not committed)
└── images/             # Full card image set, used by release builds (not committed)
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
- [ ] **Error handling** — propagate errors via `Result<T>` or a sealed class instead of swallowing exceptions (the catalogue import already shows an error with a retry)
- [x] **Type-safe navigation** — `@Serializable` route objects replace hardcoded strings; uses `composable<Route>` and `navigate(Route)`
- [x] **Persist user preferences (DataStore)** — sort order persists across app restarts via Jetpack DataStore
- [ ] **Paging 3** — paginate the card catalogue grid and collection list for efficient loading of large datasets
- [x] **ProGuard/R8** — enable `isMinifyEnabled` for release builds to shrink APK size and obfuscate code
- [x] **Kotlin Serialization** — replace Gson with compile-time serialization (no reflection, no ProGuard keep rules)
- [ ] **Unit tests** — ViewModel and Repository tests with mocked dependencies (Hilt makes this practical now)
- [ ] **UI tests** — Compose UI tests for key screens using `createComposeRule()`

