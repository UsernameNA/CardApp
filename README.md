# CardApp

A personal collection manager for **Sorcery: Contested Realm** trading cards, built for Android with Jetpack Compose.

## Features

- **Card catalogue** — browse all cards in a 3-column grid with card art, rarity indicators, and type info
- **Collection tracking** — manage your owned cards with quantity controls
- **Card scanning** — point your camera at a physical card for OCR-based identification; manual and auto-scan modes with frame fingerprinting
- **Search and filtering** — text search across name, type, subtype, and rules text; filter by card type, rarity, and element; AND/OR element matching; multi-field sorting
- **Offline-first** — card data and images are bundled with the app; no internet required after setup

## Screenshots

| Landing         | Collection      |
|-----------------|-----------------|
| *(coming soon)* | *(coming soon)* |

## Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Architecture**: single-module, ViewModel + StateFlow
- **Local storage**: Room (SQLite)
- **Images**: Coil
- **Camera**: CameraX + ML Kit Text Recognition
- **Min SDK**: 36 (Android 15)

## Building

```bash
./gradlew assembleDebug      # debug APK
./gradlew build              # debug + release
./gradlew test               # unit tests
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Data Setup

Card data and images are not committed to the repository. Run these scripts once to populate `app/src/main/assets/` before building:

### 1. Fetch card data

```bash
python3 scripts/fetch_card_data.py
```

Downloads the full card catalogue from the Sorcery TCG API and writes it to `app/src/main/assets/cards.json`.

### 2. Convert card images

```bash
python3 scripts/convert_images.py [INPUT_DIR] [QUALITY]
```

Converts PNG card images to WebP for efficient bundling. Defaults to `scripts/cards_png/` as input and quality `80`. Output goes to `app/src/main/assets/images/`.

Requires [Pillow](https://pillow.readthedocs.io/):

```bash
pip3 install Pillow
```

## Project Structure

```
app/src/main/java/com/github/usernamena/cardapp/
├── MainActivity.kt
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
└── convert_images.py   # PNG → WebP conversion via Pillow
```

## Roadmap

- [x] Landing screen
- [x] Card catalogue browser
- [x] Card scanning with camera (CameraX + ML Kit OCR)
- [x] Collection tracking with quantity management
- [x] Search and filtering
- [ ] Card detail view
- [ ] Deck builder
