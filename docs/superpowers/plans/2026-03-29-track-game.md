# Track Game Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a two-player life total counter screen where the phone sits flat between players, each seeing their life total facing them.

**Architecture:** New `TrackGameScreen` composable with local state (no ViewModel). Screen split in half — top half rotated 180°. Tap detection via `pointerInput` checks Y position relative to center to determine +1/-1. Delta indicator uses a coroutine-driven timeout.

**Tech Stack:** Jetpack Compose, `pointerInput` for tap position detection

---

### Task 1: Create TrackGameScreen

**Files:**
- Create: `app/src/main/java/com/github/username/cardapp/ui/trackgame/TrackGameScreen.kt`

- [ ] **Step 1: Create the file with the full TrackGameScreen implementation**

```kotlin
package com.github.username.cardapp.ui.trackgame

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground
import kotlinx.coroutines.delay

private const val STARTING_LIFE = 20
private const val DELTA_TIMEOUT_MS = 1500L

@Composable
fun TrackGameScreen(onBack: () -> Unit = {}) {
    var player1Life by remember { mutableIntStateOf(STARTING_LIFE) }
    var player2Life by remember { mutableIntStateOf(STARTING_LIFE) }
    var player1Delta by remember { mutableIntStateOf(0) }
    var player2Delta by remember { mutableIntStateOf(0) }
    var player1DeltaVersion by remember { mutableIntStateOf(0) }
    var player2DeltaVersion by remember { mutableIntStateOf(0) }

    // Auto-clear delta after timeout
    LaunchedEffect(player1DeltaVersion) {
        if (player1Delta != 0) {
            delay(DELTA_TIMEOUT_MS)
            player1Delta = 0
        }
    }
    LaunchedEffect(player2DeltaVersion) {
        if (player2Delta != 0) {
            delay(DELTA_TIMEOUT_MS)
            player2Delta = 0
        }
    }

    fun reset() {
        player1Life = STARTING_LIFE
        player2Life = STARTING_LIFE
        player1Delta = 0
        player2Delta = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        // Top half — rotated 180° for opposing player
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.y > size.height / 2) {
                            player2Life--
                            player2Delta--
                        } else {
                            player2Life++
                            player2Delta++
                        }
                        player2DeltaVersion++
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LifeDisplay(life = player2Life, delta = player2Delta)
        }

        // Center divider with reset
        ResetDivider(onReset = ::reset)

        // Bottom half — upright for near player
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (offset.y < size.height / 2) {
                            player1Life++
                            player1Delta++
                        } else {
                            player1Life--
                            player1Delta--
                        }
                        player1DeltaVersion++
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LifeDisplay(life = player1Life, delta = player1Delta)
        }
    }
}

@Composable
private fun LifeDisplay(life: Int, delta: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$life",
            style = Typography.displayLarge.copy(
                color = GoldLight,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(4.dp))
        if (delta != 0) {
            val text = if (delta > 0) "+$delta" else "$delta"
            Text(
                text = text,
                style = Typography.titleMedium.copy(
                    color = if (delta > 0) GoldPrimary else BurgundyAccent,
                    textAlign = TextAlign.Center,
                ),
            )
        } else {
            // Reserve space so layout doesn't jump
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResetDivider(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative lines on each side
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp),
        ) {
            drawLine(
                color = GoldMuted,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                strokeWidth = 1f,
            )
        }
        Text(
            text = "RESET",
            style = Typography.labelMedium.copy(
                color = GoldMuted,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onReset() }
                },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackGameScreenPreview() {
    CardAppTheme {
        TrackGameScreen()
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

---

### Task 2: Add navigation route and landing screen button

**Files:**
- Modify: `app/src/main/java/com/github/username/cardapp/MainActivity.kt`
- Modify: `app/src/main/java/com/github/username/cardapp/ui/landing/LandingScreen.kt`

- [ ] **Step 1: Add TrackGame route to MainActivity**

In `MainActivity.kt`, add the route object after the existing ones (after `@Serializable data class CardDetail`):

```kotlin
@Serializable object TrackGame
```

Add the import:

```kotlin
import com.github.username.cardapp.ui.trackgame.TrackGameScreen
```

Add the composable destination inside the NavHost (after the `composable<Scan>` block):

```kotlin
                    composable<TrackGame> {
                        TrackGameScreen(onBack = { navController.popBackStack() })
                    }
```

Update the `LandingScreen` call to pass the new callback:

```kotlin
                    composable<Landing> {
                        LandingScreen(
                            onViewCards = { navController.navigate(Cards) },
                            onViewCollection = { navController.navigate(Collection) },
                            onScanCards = { navController.navigate(Scan) },
                            onTrackGame = { navController.navigate(TrackGame) },
                        )
                    }
```

- [ ] **Step 2: Add onTrackGame parameter and button to LandingScreen**

In `LandingScreen.kt`, add `onTrackGame` parameter:

```kotlin
fun LandingScreen(
    onViewCards: () -> Unit = {},
    onViewCollection: () -> Unit = {},
    onScanCards: () -> Unit = {},
    onTrackGame: () -> Unit = {},
) {
```

Add the TRACK GAME button after SCAN CARDS and before the ODD/EVEN Row (between lines 130-131):

```kotlin
            ArcaneButton(text = "TRACK GAME", onClick = onTrackGame, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
```

- [ ] **Step 3: Build to verify**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

---

### Task 3: Final verification

- [ ] **Step 1: Run tests**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew test 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run lint**

Run: `cd /home/rob/AndroidStudioProjects/CardApp && ./gradlew lint 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`
