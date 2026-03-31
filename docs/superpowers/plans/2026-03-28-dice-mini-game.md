# Odd/Even Dice Mini-Game Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Odd/Even dice guessing mini-game to the landing screen with a Lottie dice roll animation.

**Architecture:** Two half-width ArcaneButtons trigger a full-screen Lottie overlay. State is local to LandingScreen (sealed interface + enum). Result auto-dismisses after 2 seconds.

**Tech Stack:** Lottie Compose 6.6.6, Jetpack Compose animation APIs

---

### Task 1: Add Lottie Compose dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Lottie version and library to version catalog**

In `gradle/libs.versions.toml`, add `lottie = "6.6.6"` to the `[versions]` block (after the `kotlinxSerializationJson` line), and add the library entry to the `[libraries]` block (after the `coil-compose` line):

```toml
# In [versions]:
lottie = "6.6.6"

# In [libraries]:
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
```

- [ ] **Step 2: Add implementation dependency to app build**

In `app/build.gradle.kts`, add this line in the `dependencies` block after the `coil-compose` line:

```kotlin
implementation(libs.lottie.compose)
```

- [ ] **Step 3: Sync and verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep lottie`

Expected: output contains `com.airbnb.android:lottie-compose:6.6.6`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add Lottie Compose dependency for dice animation"
```

---

### Task 2: Add Lottie dice roll animation asset

**Files:**
- Create: `app/src/main/res/raw/dice_roll.json`

- [ ] **Step 1: Create res/raw directory and download a dice animation**

```bash
mkdir -p /home/rob/AndroidStudioProjects/CardApp/app/src/main/res/raw
```

Download a free dice rolling Lottie JSON from LottieFiles. Search https://lottiefiles.com/search?q=dice+roll for a free animation and download its JSON file. Save it as `app/src/main/res/raw/dice_roll.json`.

If no suitable animation is found online, create a minimal placeholder animation that shows a rotating square (we can replace it later). The key requirement: the animation should be finite (not looping) and last roughly 2–3 seconds.

- [ ] **Step 2: Verify the asset is valid JSON**

```bash
python3 -c "import json; json.load(open('/home/rob/AndroidStudioProjects/CardApp/app/src/main/res/raw/dice_roll.json'))" && echo "Valid JSON"
```

Expected: `Valid JSON`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/raw/dice_roll.json
git commit -m "feat: add dice roll Lottie animation asset"
```

---

### Task 3: Add modifier parameter to ArcaneButton

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

Currently `ArcaneButton` uses `Modifier.fillMaxWidth()` hardcoded inside. We need to accept an external modifier so callers can apply `Modifier.weight(1f)` from a Row.

- [ ] **Step 1: Add modifier parameter to ArcaneButton**

Change the `ArcaneButton` signature from:

```kotlin
@Composable
private fun ArcaneButton(text: String, onClick: () -> Unit) {
```

to:

```kotlin
@Composable
private fun ArcaneButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
```

And change the Box modifier inside from:

```kotlin
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
```

to:

```kotlin
        modifier = modifier
            .height(52.dp)
```

- [ ] **Step 2: Update existing call sites to pass fillMaxWidth**

Update the three existing `ArcaneButton` calls in `LandingScreen` to explicitly pass the modifier:

```kotlin
ArcaneButton(text = "VIEW CARDS", onClick = onViewCards, modifier = Modifier.fillMaxWidth())
Spacer(Modifier.height(12.dp))
ArcaneButton(text = "MY COLLECTION", onClick = onViewCollection, modifier = Modifier.fillMaxWidth())
Spacer(Modifier.height(12.dp))
ArcaneButton(text = "SCAN CARDS", onClick = onScanCards, modifier = Modifier.fillMaxWidth())
```

- [ ] **Step 3: Build to verify no regressions**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt
git commit -m "refactor: add modifier parameter to ArcaneButton for flexible layout"
```

---

### Task 4: Add Odd/Even buttons and dice state to LandingScreen

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

- [ ] **Step 1: Add state types and imports at top of file**

Add these imports to the existing import block in `LandingScreen.kt`:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween as tweenSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.InkShadow
import kotlin.random.Random
```

Add these private types just before the `LandingScreen` composable function:

```kotlin
private enum class Parity { ODD, EVEN }

private sealed interface DiceState {
    data object Idle : DiceState
    data class Rolling(val guess: Parity) : DiceState
    data class Result(val guess: Parity, val value: Int) : DiceState
}
```

- [ ] **Step 2: Add dice state and button row to LandingScreen**

In the `LandingScreen` composable, add state at the top of the function body (before the `Box`):

```kotlin
var diceState by remember { mutableStateOf<DiceState>(DiceState.Idle) }
```

Then add the Odd/Even button row after the SCAN CARDS button inside the Column (after the last `ArcaneButton`):

```kotlin
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArcaneButton(
                    text = "ODD",
                    onClick = { diceState = DiceState.Rolling(Parity.ODD) },
                    modifier = Modifier.weight(1f),
                )
                ArcaneButton(
                    text = "EVEN",
                    onClick = { diceState = DiceState.Rolling(Parity.EVEN) },
                    modifier = Modifier.weight(1f),
                )
            }
```

- [ ] **Step 3: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt
git commit -m "feat: add Odd/Even dice buttons and DiceState to landing screen"
```

---

### Task 5: Implement DiceOverlay composable

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

- [ ] **Step 1: Add the DiceOverlay composable**

Add this private composable in `LandingScreen.kt`, before the `@Preview` functions at the bottom:

```kotlin
@Composable
private fun DiceOverlay(diceState: DiceState, onDismiss: () -> Unit) {
    // Scrim + overlay fade
    val overlayAlpha = remember { Animatable(0f) }

    // Fade in when entering Rolling or Result
    LaunchedEffect(diceState) {
        when (diceState) {
            is DiceState.Rolling -> overlayAlpha.animateTo(1f, tweenSpec(300))
            is DiceState.Idle -> overlayAlpha.animateTo(0f, tweenSpec(500))
            is DiceState.Result -> { /* already visible */ }
        }
    }

    if (overlayAlpha.value == 0f && diceState is DiceState.Idle) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(InkShadow.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},  // consume clicks so they don't pass through
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (diceState) {
            is DiceState.Rolling -> {
                DiceRollingContent(
                    guess = diceState.guess,
                    onFinished = { resultValue -> onDismiss() },
                )
            }
            is DiceState.Result -> {
                DiceResultContent(diceState = diceState, onTimeout = onDismiss)
            }
            is DiceState.Idle -> { /* fading out */ }
        }
    }
}
```

- [ ] **Step 2: Add DiceRollingContent composable**

This plays the Lottie animation and fires a callback when done:

```kotlin
@Composable
private fun DiceRollingContent(guess: Parity, onFinished: (Int) -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.dice_roll))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
    )

    // Fire result when animation completes
    var fired by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (progress >= 1f && !fired) {
            fired = true
            onFinished(Random.nextInt(1, 7))
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(200.dp),
    )
}
```

- [ ] **Step 3: Add DiceResultContent composable**

This displays the number + correct/wrong, then auto-dismisses:

```kotlin
@Composable
private fun DiceResultContent(diceState: DiceState.Result, onTimeout: () -> Unit) {
    val isCorrect = (diceState.value % 2 == 1) == (diceState.guess == Parity.ODD)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onTimeout()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${diceState.value}",
            style = Typography.displayLarge.copy(
                color = GoldLight,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isCorrect) "CORRECT!" else "WRONG!",
            style = Typography.headlineMedium.copy(
                color = if (isCorrect) GoldPrimary else BurgundyAccent,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
```

- [ ] **Step 4: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt
git commit -m "feat: add DiceOverlay with Lottie animation and result display"
```

---

### Task 6: Wire DiceOverlay into LandingScreen

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

- [ ] **Step 1: Add the overlay to the LandingScreen Box**

In the `LandingScreen` composable, add the `DiceOverlay` as the last child inside the outer `Box` (after the `Column`, after the four `CornerOrnament`s):

```kotlin
        // Dice game overlay
        DiceOverlay(
            diceState = diceState,
            onDismiss = { diceState = DiceState.Idle },
        )
```

- [ ] **Step 2: Update state transitions to show result before dismissing**

The current `DiceRollingContent.onFinished` callback goes straight to `onDismiss()` which sets `Idle`. We need it to transition through `Result` first. Replace the `DiceOverlay` composable's `Rolling` branch:

Change this in `DiceOverlay`:

```kotlin
            is DiceState.Rolling -> {
                DiceRollingContent(
                    guess = diceState.guess,
                    onFinished = { resultValue -> onDismiss() },
                )
            }
```

To:

```kotlin
            is DiceState.Rolling -> {
                DiceRollingContent(
                    guess = diceState.guess,
                    onFinished = { /* result transition handled by caller */ },
                )
            }
```

And instead, we need the parent to handle the transition. Update `DiceOverlay` to accept an `onResult` callback. Here is the full corrected `DiceOverlay` signature and Rolling branch:

Replace the entire `DiceOverlay` composable with:

```kotlin
@Composable
private fun DiceOverlay(diceState: DiceState, onResult: (Int) -> Unit, onDismiss: () -> Unit) {
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(diceState) {
        when (diceState) {
            is DiceState.Rolling -> overlayAlpha.animateTo(1f, tweenSpec(300))
            is DiceState.Idle -> overlayAlpha.animateTo(0f, tweenSpec(500))
            is DiceState.Result -> { /* already visible */ }
        }
    }

    if (overlayAlpha.value == 0f && diceState is DiceState.Idle) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(InkShadow.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (diceState) {
            is DiceState.Rolling -> {
                DiceRollingContent(
                    guess = diceState.guess,
                    onFinished = { resultValue -> onResult(resultValue) },
                )
            }
            is DiceState.Result -> {
                DiceResultContent(diceState = diceState, onTimeout = onDismiss)
            }
            is DiceState.Idle -> { /* fading out */ }
        }
    }
}
```

And update the call site in `LandingScreen`:

```kotlin
        DiceOverlay(
            diceState = diceState,
            onResult = { value ->
                val guess = (diceState as? DiceState.Rolling)?.guess ?: return@DiceOverlay
                diceState = DiceState.Result(guess, value)
            },
            onDismiss = { diceState = DiceState.Idle },
        )
```

- [ ] **Step 3: Build and verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt
git commit -m "feat: wire dice overlay into landing screen with state transitions"
```

---

### Task 7: Final verification and lint

**Files:** None (verification only)

- [ ] **Step 1: Run full build**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run unit tests**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew test 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run lint**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew lint 2>&1 | tail -10`

Expected: No new errors. Warnings are acceptable.

- [ ] **Step 4: Verify the animation flow manually**

Install on device/emulator and confirm:
1. ODD and EVEN buttons appear side-by-side below SCAN CARDS
2. Tapping either triggers the Lottie animation overlay
3. After animation ends, result number + CORRECT/WRONG shows
4. Overlay auto-dismisses after ~2 seconds
5. Can immediately tap again for another roll
