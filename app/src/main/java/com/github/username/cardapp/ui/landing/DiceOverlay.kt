package com.github.username.cardapp.ui.landing

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.github.username.cardapp.ui.theme.InkShadow
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun DiceOverlay(diceState: DiceState, onResult: (Parity, Int) -> Unit, onDismiss: () -> Unit) {
    if (LocalInspectionMode.current) return

    val overlayAlpha = remember { Animatable(0f) }

    val currentState by rememberUpdatedState(diceState)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val context = LocalContext.current
    var diceReady by remember { mutableStateOf(false) }
    // Created on the first roll, then kept and reused so later rolls start without a page reload
    var diceWebView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { diceWebView?.let(::destroyDiceWebView) }
    }

    // The result stays on screen while the overlay fades out after a dismissal
    var fadingResult by remember { mutableStateOf<DiceState.Result?>(null) }

    // Mid-roll taps may cancel, after a grace period so the tap that started
    // the roll can't immediately dismiss it
    var cancelGraceElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(diceState, diceReady) {
        when (diceState) {
            is DiceState.Rolling -> {
                fadingResult = null
                cancelGraceElapsed = false
                val webView = diceWebView ?: createDiceWebView(
                    context = context,
                    onReady = { diceReady = true },
                    onValue = { value ->
                        val rolling = currentState as? DiceState.Rolling
                        if (rolling != null) {
                            if (value in 1..20) {
                                currentOnResult(rolling.guess, value)
                            } else {
                                // Failed roll — dismiss without inventing a verdict
                                currentOnDismiss()
                            }
                        }
                    },
                    onRenderProcessGone = {
                        // The client has already destroyed it; the next roll builds a fresh one
                        diceWebView = null
                        diceReady = false
                        if (currentState !is DiceState.Idle) currentOnDismiss()
                    },
                ).also { diceWebView = it }
                if (diceReady) webView.evaluateJavascript("window.startRoll()", null)
                overlayAlpha.animateTo(1f, tween(300))
                delay(700.milliseconds)
                cancelGraceElapsed = true
                // Watchdog: the page's own 12s timeout should fire first;
                // this covers a WebView that never reports back at all
                delay(14_000.milliseconds)
                currentOnDismiss()
            }
            is DiceState.Idle -> {
                if (diceReady) diceWebView?.evaluateJavascript("window.clearDice()", null)
                overlayAlpha.animateTo(0f, tween(500))
                fadingResult = null
            }
            is DiceState.Result -> fadingResult = diceState
        }
    }

    BackHandler(enabled = diceState !is DiceState.Idle) { onDismiss() }

    val dismissing = diceState is DiceState.Idle
    val shownResult = diceState as? DiceState.Result ?: if (dismissing) fadingResult else null
    if (dismissing && shownResult == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .then(
                if (shownResult != null) Modifier.background(InkShadow.copy(alpha = 0.7f))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        // While fading out nothing here takes pointer input, so taps reach the screen
        // beneath; the WebView would swallow them, so the die goes immediately
        if (!dismissing) {
            diceWebView?.let { DiceWebViewHost(webView = it) }
            // Touch-intercepting layer over the WebView: tap to dismiss a result,
            // or cancel a roll once the grace period has passed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {
                            when (diceState) {
                                is DiceState.Result -> onDismiss()
                                is DiceState.Rolling if cancelGraceElapsed -> onDismiss()
                                else -> {}
                            }
                        },
                    ),
            )
        }
        if (shownResult != null) {
            DiceResultContent(diceState = shownResult, onTimeout = onDismiss)
        }
    }
}

@Composable
private fun DiceWebViewHost(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        modifier = modifier.fillMaxSize(),
    )
}
