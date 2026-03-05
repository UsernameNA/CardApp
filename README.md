# CardApp

A personal collection manager for **Sorcery: Contested Realm** trading cards, built for Android with Jetpack Compose.

## Features

- **Collection browser** — browse your catalogued cards in a 3-column grid with card art, rarity indicators, and set information
- **Offline-first** — card data and images are bundled with the app; no internet required after setup
- **Card scanning** *(coming soon)* — point your camera at a physical card and have it identified and added to your collection automatically

## Screenshots

| Landing | Collection |
|---------|------------|
| *(coming soon)* | *(coming soon)* |

## Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Architecture**: single-module, ViewModel + StateFlow
- **Local storage**: Room (SQLite)
- **Images**: Coil
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
    ├── landing/        # Landing screen
    ├── collection/     # Card grid + ViewModel
    └── theme/          # Colour palette, typography, modifiers
scripts/
├── fetch_card_data.py  # Downloads cards.json from API
└── convert_images.py   # PNG → WebP conversion via Pillow
```

## Roadmap

- [x] Landing screen
- [x] Collection browser
- [ ] Card scan with camera (CameraX + on-device image matching)
- [ ] Card detail view
- [ ] Collection filtering and search
- [ ] Deck builder
