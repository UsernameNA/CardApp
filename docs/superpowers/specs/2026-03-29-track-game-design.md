# Track Game — Life Total Counter Design Spec

## Overview

A two-player life total counter for Sorcery: Contested Realm. The phone sits flat between two players. Each player sees their life total facing them — the bottom player's number is upright, the top player's number is rotated 180°. Players tap their number to adjust life (top half = +1, bottom half = -1). A running delta indicator shows cumulative taps before fading. A reset button on the center divider resets both to 20.

## Navigation

- Add `@Serializable object TrackGame` route to `MainActivity.kt`
- Add `composable<TrackGame> { TrackGameScreen(onBack = { navController.popBackStack() }) }` to the NavHost
- Add "TRACK GAME" `ArcaneButton` to `LandingScreen` above the ODD/EVEN row, with `onTrackGame` callback
- `LandingScreen` gets a new `onTrackGame: () -> Unit = {}` parameter

## Screen Layout

The screen is split into two equal halves vertically, each containing one player's life counter:

```
┌──────────────────────┐
│                      │
│      ╻ ╺━╸           │  ← Top player (rotated 180°)
│      20              │
│      +2              │  ← delta indicator
│                      │
├───── ◆ RESET ◆ ─────┤  ← center divider with reset
│                      │
│      -1              │  ← delta indicator
│      20              │
│      ╻ ╺━╸           │  ← Bottom player (upright)
│                      │
└──────────────────────┘
```

### Top Half
- Rotated 180° so it reads correctly for the player sitting across the table
- Life total centered, large `Typography.displayLarge` in `GoldLight`
- Tap upper region (from that player's perspective) = +1, lower region = -1
- Since the whole half is rotated, in screen coordinates: tap above center = -1, tap below center = +1

### Bottom Half
- Upright orientation
- Same layout: life total centered, tap above center = +1, tap below center = -1

### Center Divider
- `OrnamentalDivider` spanning full width
- "RESET" text or button centered on the divider in `GoldMuted`
- Tapping reset sets both life totals back to 20 and clears any delta indicators

## Delta Indicator

When a player taps to change life:
- A small text appears near the life total showing the cumulative delta: "+1", "+2", "-1", "-3", etc.
- Styled in `CreamFaded`, smaller typography (e.g., `Typography.titleMedium`)
- Each new tap within the window resets the fade timer and updates the count
- After ~1.5 seconds of no taps, the indicator fades out and the cumulative count resets
- Positive deltas shown in `GoldPrimary`, negative in `BurgundyAccent`

## Interaction

Each player half is a single `clickable`/`pointerInput` region. The tap position relative to the center of the half determines +1 or -1:
- Tap above the vertical center of the half → +1 (from that player's perspective)
- Tap below the vertical center → -1

Since the top half is rotated 180°, the coordinate mapping inverts naturally — no special handling needed if we apply the rotation to the entire half including the tap target.

## State

All local composable state — no ViewModel, no persistence:
- `player1Life: Int` (bottom player, starts at 20)
- `player2Life: Int` (top player, starts at 20)
- `player1Delta: Int` (running tap count, resets after timeout)
- `player2Delta: Int` (running tap count, resets after timeout)

## Styling

- `leatherBackground()` on the full screen
- Life totals in `Typography.displayLarge`, `GoldLight`
- Delta indicators in `Typography.titleMedium`, `GoldPrimary` (positive) / `BurgundyAccent` (negative)
- Reset text in `Typography.labelMedium`, `GoldMuted`
- No system bars padding needed — full-screen immersive feel, but use `systemBarsPadding()` on the reset area to avoid overlap with status bar/nav bar

## Files

- Create: `app/src/main/java/com/github/username/cardapp/ui/trackgame/TrackGameScreen.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/MainActivity.kt` (add route)
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt` (add button + callback)
