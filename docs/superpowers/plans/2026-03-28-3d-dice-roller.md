# 3D WebView Dice Roller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Lottie dice animation with a physics-based 3D d20 roller (Three.js + Cannon.js) rendered in a WebView, where the die visually lands on the actual result.

**Architecture:** Bundle the 3d-die-roller JS library in `assets/dice/`, create a bridge HTML page that auto-throws a d20 and calls back into Kotlin via `@JavascriptInterface`. Replace the Lottie-based `DiceRollingContent` with a WebView `AndroidView`. Remove all Lottie dependencies.

**Tech Stack:** Three.js (WebGL), Cannon.js (physics), Android WebView, `@JavascriptInterface`

---

### Task 1: Download and bundle 3d-die-roller JS assets

**Files:**
- Create: `app/src/main/assets/dice/three.min.js`
- Create: `app/src/main/assets/dice/cannon.min.js`
- Create: `app/src/main/assets/dice/teal.js`
- Create: `app/src/main/assets/dice/dice.js`

- [ ] **Step 1: Create assets/dice/ directory**

```bash
mkdir -p /home/rob/AndroidStudioProjects/CardApp/app/src/main/assets/dice
```

- [ ] **Step 2: Download the four JS files from the 3d-die-roller repo**

```bash
cd /home/rob/AndroidStudioProjects/CardApp/app/src/main/assets/dice
curl -sO https://raw.githubusercontent.com/emanchado/3d-die-roller/master/libs/three.min.js
curl -sO https://raw.githubusercontent.com/emanchado/3d-die-roller/master/libs/cannon.min.js
curl -sO https://raw.githubusercontent.com/emanchado/3d-die-roller/master/teal.js
curl -sO https://raw.githubusercontent.com/emanchado/3d-die-roller/master/dice/dice.js
```

- [ ] **Step 3: Verify all four files downloaded**

```bash
ls -la /home/rob/AndroidStudioProjects/CardApp/app/src/main/assets/dice/
```

Expected: four `.js` files, each non-empty (three.min.js ~500KB, cannon.min.js ~200KB, teal.js ~4KB, dice.js ~20KB).

- [ ] **Step 4: Commit**

```bash
cd /home/rob/AndroidStudioProjects/CardApp
git add app/src/main/assets/dice/
git commit -m "feat: bundle 3d-die-roller JS libraries for WebGL dice"
```

---

### Task 2: Create dice_bridge.html

**Files:**
- Create: `app/src/main/assets/dice/dice_bridge.html`

This is the HTML page loaded by the WebView. It:
- Loads the four JS libraries
- On load, initializes the dice box filling the viewport
- Immediately auto-throws a single d20
- Has a black background (matches the overlay scrim)
- When the die settles, calls `Android.onDiceResult(faceValue)` (1–20)
- No UI chrome — no selectors, labels, help text, buttons

- [ ] **Step 1: Create dice_bridge.html**

Write this file to `app/src/main/assets/dice/dice_bridge.html`:

```html
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
  * { margin: 0; padding: 0; }
  body { background: #000; overflow: hidden; }
  #canvas { width: 100vw; height: 100vh; }
</style>
</head>
<body>
<div id="canvas"></div>

<script src="three.min.js"></script>
<script src="cannon.min.js"></script>
<script src="teal.js"></script>
<script src="dice.js"></script>
<script>
(function() {
    var canvas = document.getElementById('canvas');
    var w = window.innerWidth;
    var h = window.innerHeight;
    canvas.style.width = w + 'px';
    canvas.style.height = h + 'px';

    var box = new $t.dice.dieBox(canvas, { w: w, h: h });

    var notation = {
        set: [{ type: 'd20' }],
        constant: 0
    };

    // Random throw direction and boost
    var coords = {
        x: (Math.random() * 2 - 1) * w,
        y: -(Math.random() * 2 - 1) * h
    };
    var dist = Math.sqrt(coords.x * coords.x + coords.y * coords.y);
    var boost = (Math.random() + 3) * dist;
    coords.x /= dist;
    coords.y /= dist;

    function beforeRoll() { /* no UI to hide */ }

    function afterRoll(notation, result) {
        var value = result[0];
        // Call back into Android
        if (window.Android && Android.onDiceResult) {
            Android.onDiceResult(value);
        }
    }

    // Auto-throw on load
    box.rollDice(notation, coords, boost, beforeRoll, afterRoll);
})();
</script>
</body>
</html>
```

- [ ] **Step 2: Verify the HTML is valid**

```bash
grep -c 'Android.onDiceResult' /home/rob/AndroidStudioProjects/CardApp/app/src/main/assets/dice/dice_bridge.html
```

Expected: `1`

- [ ] **Step 3: Commit**

```bash
cd /home/rob/AndroidStudioProjects/CardApp
git add app/src/main/assets/dice/dice_bridge.html
git commit -m "feat: add dice bridge HTML page for WebView d20 roller"
```

---

### Task 3: Replace DiceRollingContent with WebView

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

Replace the Lottie-based `DiceRollingContent` with a WebView-based implementation. Remove all Lottie imports and add WebView/AndroidView imports.

- [ ] **Step 1: Replace imports**

Remove these imports:
```kotlin
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.size
```

Add these imports:
```kotlin
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
```

Note: `import androidx.compose.foundation.layout.size` is used by `CornerOrnament` (`Modifier.size(52.dp)`) and by `TitleSection` (`Modifier.size(180.dp)`), so do NOT remove it. Only remove the Lottie imports, `Random`, `flow.first`, and `snapshotFlow`.

- [ ] **Step 2: Replace DiceRollingContent composable**

Replace the entire `DiceRollingContent` function (lines 421-441 in current file):

Old:
```kotlin
@Composable
private fun DiceRollingContent(onFinished: (Int) -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.dice_roll))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
    )

    LaunchedEffect(Unit) {
        snapshotFlow { progress }
            .first { it >= 1f }
        onFinished(Random.nextInt(1, 7))
    }

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(200.dp),
    )
}
```

New:
```kotlin
private class DiceBridge(private val onResult: (Int) -> Unit) {
    @JavascriptInterface
    fun onDiceResult(value: Int) {
        Handler(Looper.getMainLooper()).post { onResult(value) }
    }
}

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

Note: `DiceBridge` is a private class inside `LandingScreen.kt`. This is acceptable — it's a tiny helper class colocated with its only consumer, same as the `Parity` enum and `DiceState` sealed interface.

- [ ] **Step 3: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd /home/rob/AndroidStudioProjects/CardApp
git add app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt
git commit -m "feat: replace Lottie with WebView 3D d20 roller in DiceRollingContent"
```

---

### Task 4: Remove Lottie dependency and assets

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/main/res/raw/dice_roll.json`

- [ ] **Step 1: Remove Lottie from version catalog**

In `gradle/libs.versions.toml`:
- Remove the line `lottie = "6.6.6"` from `[versions]`
- Remove the line `lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }` from `[libraries]`

- [ ] **Step 2: Remove Lottie from build.gradle.kts**

In `app/build.gradle.kts`, remove:
```kotlin
implementation(libs.lottie.compose)
```

- [ ] **Step 3: Delete the Lottie animation asset**

```bash
rm /home/rob/AndroidStudioProjects/CardApp/app/src/main/res/raw/dice_roll.json
rmdir /home/rob/AndroidStudioProjects/CardApp/app/src/main/res/raw/ 2>/dev/null || true
```

- [ ] **Step 4: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
cd /home/rob/AndroidStudioProjects/CardApp
git add -u gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/raw/
git commit -m "chore: remove Lottie dependency and dice animation asset"
```

---

### Task 5: Final verification

**Files:** None (verification only)

- [ ] **Step 1: Run full build**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run unit tests**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew test 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run lint**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew lint 2>&1 | tail -10`

Expected: No new errors.

- [ ] **Step 4: Verify no Lottie references remain**

```bash
grep -r "lottie" /home/rob/AndroidStudioProjects/CardApp/app/src/ --include="*.kt" -l
grep -r "lottie" /home/rob/AndroidStudioProjects/CardApp/gradle/ --include="*.toml" -l
grep -r "lottie" /home/rob/AndroidStudioProjects/CardApp/app/build.gradle.kts -l
```

Expected: no output from any of the three commands.

- [ ] **Step 5: Verify assets are bundled**

```bash
ls /home/rob/AndroidStudioProjects/CardApp/app/src/main/assets/dice/
```

Expected: `cannon.min.js  dice.js  dice_bridge.html  teal.js  three.min.js`
