# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CardApp** is an Android application built with Kotlin and Jetpack Compose targeting Android 15 (API 36). Currently a starter template with Material 3 theming and dynamic color support.

## Build Commands

```bash
./gradlew build                    # Full debug + release build
./gradlew assembleDebug            # Debug APK only
./gradlew test                     # Unit tests (JVM)
./gradlew connectedAndroidTest     # Instrumented tests (device/emulator required)
./gradlew testDebugUnitTest        # Run a single test variant
./gradlew lint                     # Run lint checks
```

To run a single test class:
```bash
./gradlew testDebugUnitTest --tests "com.github.username.cardapp.ExampleUnitTest"
```

## Architecture

Single-module Android app (`app/`). Entry point is `MainActivity`, which sets up the Compose content with `CardAppTheme`.

**UI layer**: All UI is Jetpack Compose. There is no XML layout system in use. The only composables currently are in `MainActivity.kt` (the `Greeting` composable).

**Theming** (`ui/theme/`):
- `CardAppTheme` in `Theme.kt` wraps content; supports dynamic color (Material You, Android 12+) and automatic dark/light mode via `isSystemInDarkTheme()`
- Color palette defined in `Color.kt`; typography in `Type.kt`
- Material 3 throughout — use `MaterialTheme.colorScheme`, `MaterialTheme.typography`

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose (via Compose BOM 2024.09.00)
- **Design**: Material Design 3
- **Min/Target SDK**: 36 (Android 15)
- **Build**: Gradle 9.2.1 (KTS syntax), AGP 9.0.1
- **Dependencies**: Managed via version catalog at `gradle/libs.versions.toml`

## Testing

- Unit tests: `app/src/test/` — JUnit 4, run on JVM
- Instrumented tests: `app/src/androidTest/` — AndroidJUnit4 + Espresso + Compose UI test
- Compose UI testing uses `createComposeRule()` / `ComposeTestRule`
