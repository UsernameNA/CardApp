# 3D WebView Dice Roller — Design Spec

## Overview

Replace the Lottie dice animation with a physics-based 3D d20 roller using the [3d-die-roller](https://github.com/emanchado/3d-die-roller) library (Three.js + Cannon.js) rendered in a WebView. The die rolls with real physics and the landed face value is communicated back to Kotlin via a JavaScript interface. Remove all Lottie dependencies.

## Changes from Previous Implementation

- **Remove**: Lottie Compose dependency (libs.versions.toml, build.gradle.kts), `res/raw/dice_roll.json`
- **Remove**: `LottieAnimation`/`rememberLottieComposition`/`animateLottieCompositionAsState` imports and usage in `DiceRollingContent`
- **Add**: Bundled JS/HTML assets in `app/src/main/assets/dice/`
- **Replace**: `DiceRollingContent` internals — from Lottie composable to `AndroidView` wrapping a `WebView`
- **Change**: Random result no longer generated in Kotlin — comes from the physics simulation via JS bridge
- **Change**: d20 (1–20) instead of d6 (1–6)

## Bundled Assets

All files go in `app/src/main/assets/dice/`:

| File | Source | Notes |
|---|---|---|
| `three.min.js` | Three.js r69 (from repo's `libs/`) | WebGL renderer |
| `cannon.min.js` | Cannon.js (from repo's `libs/`) | Physics engine |
| `teal.js` | From repo root | DOM utility library |
| `dice.js` | From repo's `dice/` | Dice geometry, materials, physics, rendering |
| `dice_bridge.html` | New — custom | Minimal HTML page, auto-throws d20, calls Android bridge |

### dice_bridge.html

A stripped-down HTML page that:
1. Loads the four JS files
2. On load, initializes the dice box with the full viewport
3. Immediately auto-throws a single d20 with random velocity
4. Has a transparent/black background (matches the overlay scrim)
5. When the die settles, calls `Android.onDiceResult(faceValue)` where `faceValue` is 1–20
6. No UI chrome — no selectors, labels, help text, or control panels

The auto-throw logic is adapted from `main.js`'s throw button handler: generate random coordinates and boost, call `box.rollDice()` with notation `{set: [{type: "d20", ...}], constant: 0}`, and use the `afterRoll` callback to fire the bridge call.

## Kotlin Changes

### DiceRollingContent

Replace the Lottie-based composable with a WebView:

```kotlin
@Composable
private fun DiceRollingContent(onFinished: (Int) -> Unit) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(DiceBridge(onFinished), "Android")
                loadUrl("file:///android_asset/dice/dice_bridge.html")
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
```

### DiceBridge

A simple class with a `@JavascriptInterface` method:

```kotlin
private class DiceBridge(private val onResult: (Int) -> Unit) {
    @JavascriptInterface
    fun onDiceResult(value: Int) {
        onResult(value)
    }
}
```

Note: `@JavascriptInterface` callbacks run on a WebView background thread, not the main thread. The `onResult` lambda must post to the main thread. Since `DiceOverlay` uses `onResult` to set Compose state (`diceState = ...`), and Compose state writes are thread-safe via snapshot system, this should work. But if issues arise, wrap in `Handler(Looper.getMainLooper()).post { ... }`.

### DiceOverlay

- The `DiceRollingContent` now takes `Modifier.fillMaxSize()` instead of `Modifier.size(200.dp)` — the WebView needs the full overlay area to render the 3D scene
- The overlay scrim (`InkShadow.copy(alpha = 0.7f)`) remains, but the WebView's transparent background lets the scrim show through around the die

### DiceState

No changes to the sealed interface. `Result(guess: Parity, value: Int)` now holds a value in 1–20 instead of 1–6. The parity check `(value % 2 == 1) == (guess == Parity.ODD)` works the same for any integer.

## Dependency Removal

- `gradle/libs.versions.toml`: remove `lottie` from `[versions]` and `lottie-compose` from `[libraries]`
- `app/build.gradle.kts`: remove `implementation(libs.lottie.compose)`
- Delete `app/src/main/res/raw/dice_roll.json`
- Remove all Lottie imports from `LandingScreen.kt`

## ProGuard

No changes needed — WebView is a platform API. The `@JavascriptInterface` annotation is already kept by default Android ProGuard rules.
