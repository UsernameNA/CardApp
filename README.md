# CardApp

<img src="app/src/main/ic_launcher-playstore.png" width="512" alt="CardApp icon">

A personal collection manager for **Sorcery: Contested Realm** trading cards, built for Android with Jetpack Compose.

## Features

- **Card catalogue** — browse all cards with rarity indicators, type info, and market prices
- **Collection tracking** — manage your owned cards with quantity controls
- **Card scanning** — point your camera at a physical card for OCR-based identification; manual and auto-scan modes with frame fingerprinting. Each scan reads the card over several frames and merges the scores, and a match that only narrowly beats the runner-up is sent to the correction panel rather than accepted
- **Card FAQs** — official rulings from the sorcerytcg.com codex shown on the card detail screen
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
- **Images**: Coil 3 (no network module)
- **Camera**: CameraX + ML Kit Text Recognition
- **Build**: R8 minification + resource shrinking enabled
- **Min SDK**: 36 (Android 16), target SDK 37

## Installation

Download the latest APK from the [Releases](https://github.com/UsernameNA/CardApp/releases) page and install it on your Android 16+ device.

Releases are around 340MB because the full card art ships inside the APK. A release that is only tens of MB is missing its images — see [Releasing](#releasing).

## Building

```bash
./gradlew assembleDebug                  # debug APK (small image subset, fast)
./gradlew assembleRelease                # release APK (all card images included)
./gradlew testDebugUnitTest lintDebug    # unit tests and lint, as CI runs them
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
Release APK: `app/build/outputs/apk/release/app-release.apk`

The debug build's application ID ends in `.debug`, so it installs alongside the release build instead of replacing it.

Release builds automatically copy the full image set from `scripts/images/` into the APK. Debug builds use a small subset from `app/src/main/assets/images/` for faster iteration.

## Releasing

**Release artifacts must be built locally.** `scripts/images/` is gitignored (~293MB), so a CI checkout has nothing for `copyReleaseImages` to bundle. v1.4.0 and v1.4.1 shipped that way — every card blank, 56MB against v1.3.0's 341MB — so the task now fails when it finds fewer than 3,000 images. `-PallowMissingReleaseImages=true` waives the check for builds that will never be published, such as the CI smoke test.

The `Release APK` workflow is therefore `workflow_dispatch`-only, waives the image check, and uploads to a build artifact instead of publishing. The `CI` workflow runs the unit tests and lint on every push and pull request. Signing needs `keystore.properties` and `release.keystore` in the repo root (both gitignored), or the `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables; without either, release packaging fails.

```bash
# 1. bump versionCode and versionName in app/build.gradle.kts
./gradlew test assembleRelease

# 2. sanity-check the artifact
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c 'assets/images/.*webp'   # expect ~3000
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk              # must match the previous release

# 3. tag and publish, attaching the R8 mapping so release stack traces can be deobfuscated
git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
cp app/build/outputs/mapping/release/mapping.txt mapping-vX.Y.Z.txt
gh release create vX.Y.Z --title "vX.Y.Z" --notes-file notes.md \
  app/build/outputs/apk/release/app-release.apk mapping-vX.Y.Z.txt
```

The certificate check matters: a mismatch is silent until a user tries to update in place and is told to uninstall first. The mapping file only matches the build that produced the APK and cannot be regenerated later, so archive it with every release.

## Data Setup

Card data, FAQs and prices are committed under `app/src/main/assets/`. Card images live in `scripts/images/` (not committed due to size) and are needed for release builds.

To refresh everything (stdlib only, except Pillow for images):

```bash
pip3 install -r scripts/requirements.txt
python3 scripts/fetch_card_data.py   # -> app/src/main/assets/cards.json
python3 scripts/fetch_images.py      # -> scripts/images/*.webp (only missing ones)
python3 scripts/fetch_faqs.py        # -> app/src/main/assets/faqs.json
python3 scripts/fetch_prices.py      # -> app/src/main/assets/prices.json
```

Each fetcher exits with an error and leaves its file untouched if the source returns something unexpected or suspiciously small.

### 1. Card data

Downloads the catalogue from `api.sorcerytcg.com/api/cards` and converts it into the format the app reads. The API is the source of truth; the existing `cards.json` fills in what the API no longer returns (flavour text, some avatar rarity and life values, a few site subtypes) and pins set and printing order so existing cards keep their display image. Run it on top of the committed file, not from scratch.

The API's set dates are database timestamps, so real release dates are kept in `SET_RELEASES` in the script. When a new set appears the script says so; add its release date there. The API's "Promo" set is renamed "Promotional" to match the app.

Printing slugs are the filenames the app looks up in `assets/images/`, so a change to their format upstream will break every card image until the art is renamed to match.

The app re-imports its database copy whenever `cards.json`, `prices.json`, or the set of bundled images differs from what it last imported, so an app update carries new data to existing installs. The collection survives re-imports; entries whose card leaves the catalogue are kept, hidden, and reappear if it returns. A card's display image is its earliest standard print that has bundled art.

### 2. Card images

Downloads art for every printing in `cards.json` that has no `scripts/images/<slug>.webp` yet, from the publisher's public "Card Images (API)" Google Drive folder, and converts it to WebP (quality 80, original 380×531). Run it after fetching card data. A handful of promo printings are not in the folder; those cards fall back to another printing's art.

### 3. Card FAQs

Fetches official rulings from the sorcerytcg.com codex (`/api/trpc/codex.listFaqs`) and writes them to `app/src/main/assets/faqs.json`, grouped by card name.

### 4. Market prices

Fetches current market prices from TCGPlayer for all Sorcery sets. Run periodically to keep prices up to date. If requests start failing, the `mpfev` value in the script's URL (TCGPlayer's frontend version) has probably changed.

"Arthurian Legends" and "Arthurian Legends Promo" are distinct sets in this data and must not be merged. Products with no market price yet (common for a new set) fall back to their median or lowest listing.

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
├── fetch_card_data.py  # Downloads and converts cards.json from the Sorcery TCG API
├── fetch_images.py     # Downloads missing card art from the publisher's Drive folder
├── fetch_faqs.py       # Fetches card rulings from the sorcerytcg.com codex
├── fetch_prices.py     # Fetches market prices from TCGPlayer
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
- [x] **Kotlin Serialization** — replace Gson with compile-time serialization (no reflection; one ProGuard rule keeps the app's `@Serializable` classes)
- [ ] **Unit tests** — 130+ JVM and Robolectric tests cover the DAO, repository import, migrations, filtering, pricing and OCR scoring; ViewModel tests are still missing
- [ ] **UI tests** — Compose UI tests for key screens using `createComposeRule()`

