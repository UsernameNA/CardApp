# Odd/Even Dice Mini-Game — Design Spec

## Overview

Add an Odd/Even guessing mini-game to the landing screen. Two half-width buttons ("ODD" / "EVEN") sit side-by-side below the existing navigation buttons. Tapping one plays a Lottie 3D dice roll animation as a full-screen overlay, generates a random 1–6 result, and shows a correct/wrong banner that auto-fades after ~2 seconds. Purely ephemeral — no state persisted.

## UI Changes

### Landing Screen Buttons

Add a `Row` below the existing SCAN CARDS button containing two `ArcaneButton`s:
- "ODD" (left, `flex = 1`)
- "EVEN" (right, `flex = 1`)
- 12dp horizontal gap between them, same 12dp vertical spacing as the other buttons
- Same `ArcaneButton` styling (double border, leather background, press animation)

`ArcaneButton` needs a `modifier` parameter so the `Row` children can apply `Modifier.weight(1f)` instead of the default `fillMaxWidth()`.

### Dice Roll Overlay

When the user taps ODD or EVEN:

1. **Scrim** — semi-transparent dark overlay (`InkShadow.copy(alpha = 0.7f)`) covers the full screen
2. **Lottie animation** — centered dice roll animation plays once (`iterations = 1`). Sized ~200dp.
3. **Result** — when the animation ends:
   - Random Int 1–6 generated
   - Compare parity against the user's guess (ODD or EVEN)
   - Display result text centered on screen:
     - Die number in large display font
     - "CORRECT!" in `GoldPrimary` or "WRONG!" in `BurgundyAccent` below it
4. **Auto-dismiss** — result banner + scrim fade out over ~500ms after a 2-second hold, returning to normal landing screen

### State Management

Local `LandingScreen` composable state only — no ViewModel needed:
- `diceState`: sealed interface — `Idle`, `Rolling(guess: Parity)`, `Result(guess: Parity, value: Int)`
- `Parity` enum: `ODD`, `EVEN`
- Reset to `Idle` after fade-out completes

## Dependencies

### Lottie Compose

Add to `gradle/libs.versions.toml`:
```toml
[versions]
lottie = "6.6.6"

[libraries]
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
```

Add to `app/build.gradle.kts`:
```kotlin
implementation(libs.lottie.compose)
```

### Lottie Animation Asset

Bundle a free dice-roll animation JSON in `app/src/main/res/raw/dice_roll.json`. Source from LottieFiles (free license). The animation is decorative — the actual result is determined by `Random.nextInt(1, 7)`, not by the animation content.

## Component Breakdown

All new code in `LandingScreen.kt` (keeping one-class-per-file rule — these are private composables, not classes):

- `DiceGameButtons(onOdd, onEven)` — the `Row` with two `ArcaneButton`s
- `DiceOverlay(diceState, onDismiss)` — full-screen overlay with scrim, Lottie, and result text
- State holders: `DiceState` sealed interface, `Parity` enum (private to file)

## Animation Flow

```
User taps ODD/EVEN
  → diceState = Rolling(guess)
  → Overlay appears (scrim fade-in ~300ms)
  → Lottie plays (~2-3s depending on asset)
  → On animation end: generate random 1-6, diceState = Result(guess, value)
  → Result text fades in (~300ms)
  → Hold 2 seconds
  → Entire overlay fades out (~500ms)
  → diceState = Idle
```

## ProGuard

No additional rules needed — Lottie Compose ships its own consumer ProGuard rules.
