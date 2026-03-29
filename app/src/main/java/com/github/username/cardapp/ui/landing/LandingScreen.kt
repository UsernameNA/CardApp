package com.github.username.cardapp.ui.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.github.username.cardapp.R
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.InkShadow
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground

private enum class Parity { ODD, EVEN }

private sealed interface DiceState {
    data object Idle : DiceState
    data class Rolling(val guess: Parity) : DiceState
    data class Result(val guess: Parity, val value: Int) : DiceState
}

@Composable
fun LandingScreen(
    onViewCards: () -> Unit = {},
    onViewCollection: () -> Unit = {},
    onScanCards: () -> Unit = {},
) {
    var diceState by remember { mutableStateOf<DiceState>(DiceState.Idle) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        // Gilded corner ornaments
        CornerOrnament(
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            flipX = false, flipY = false,
        )
        CornerOrnament(
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            flipX = true, flipY = false,
        )
        CornerOrnament(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            flipX = false, flipY = true,
        )
        CornerOrnament(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            flipX = true, flipY = true,
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TitleSection()

            Spacer(Modifier.height(56.dp))

            ArcaneButton(text = "VIEW CARDS", onClick = onViewCards, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ArcaneButton(text = "MY COLLECTION", onClick = onViewCollection, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ArcaneButton(text = "SCAN CARDS", onClick = onScanCards, modifier = Modifier.fillMaxWidth())
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
        }

        // Dice game overlay
        DiceOverlay(
            diceState = diceState,
            onResult = { guess, value ->
                diceState = DiceState.Result(guess, value)
            },
            onDismiss = { diceState = DiceState.Idle },
        )
    }
}

@Composable
private fun TitleSection() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            GoldMuted,
            GoldMuted,
            GoldPrimary,
            GoldLight,
            GoldPrimary,
            GoldMuted,
            GoldMuted,
        ),
        start = Offset(shimmerX - 200f, 0f),
        end = Offset(shimmerX + 200f, 0f),
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CARDAPP",
            style = Typography.displayLarge.copy(
                brush = shimmerBrush,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        // Raven landing on the "D" — shimmer brush applied via SrcIn blend
        val ravenPainter = painterResource(R.drawable.ic_launcher_foreground)
        Spacer(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 18.dp, y = (-58).dp)
                .graphicsLayer { alpha = 0.99f } // force offscreen buffer for BlendMode
                .drawWithCache {
                    onDrawBehind {
                        with(ravenPainter) {
                            draw(size = Size(size.width, size.height))
                        }
                        drawRect(
                            brush = shimmerBrush,
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                },
        )
    }

    Spacer(Modifier.height(16.dp))
    OrnamentalDivider(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))

    Text(
        text = "Sorcery Contested Realm",
        style = Typography.displaySmall.copy(
            color = CreamFaded,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OrnamentalDivider(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(20.dp)) {
        val midY = size.height / 2f
        val halfW = size.width / 2f
        val diamondR = 4.dp.toPx()
        val gap = diamondR * 2.8f
        val dotR = 1.8.dp.toPx()
        val dotGap = gap + dotR * 3.2f
        val lineAlpha = 0.55f

        drawLine(
            color = GoldMuted.copy(alpha = lineAlpha),
            start = Offset(0f, midY),
            end = Offset(halfW - gap, midY),
            strokeWidth = 0.6f,
        )
        drawLine(
            color = GoldMuted.copy(alpha = lineAlpha),
            start = Offset(halfW + gap, midY),
            end = Offset(size.width, midY),
            strokeWidth = 0.6f,
        )

        // Center diamond
        val path = Path().apply {
            moveTo(halfW, midY - diamondR)
            lineTo(halfW + diamondR * 0.65f, midY)
            lineTo(halfW, midY + diamondR)
            lineTo(halfW - diamondR * 0.65f, midY)
            close()
        }
        drawPath(path, color = GoldPrimary)

        // Flanking dots
        drawCircle(GoldMuted.copy(alpha = 0.8f), dotR, Offset(halfW - dotGap, midY))
        drawCircle(GoldMuted.copy(alpha = 0.8f), dotR, Offset(halfW + dotGap, midY))
    }
}

@Composable
private fun ArcaneButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "btnScale",
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .border(
                width = 0.8.dp,
                color = if (pressed) GoldPrimary else GoldMuted,
                shape = RoundedCornerShape(2.dp),
            )
            .padding(2.dp)
            .border(
                width = 0.5.dp,
                color = GoldDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(1.dp),
            )
            .background(
                color = if (pressed) LeatherMid else LeatherMid.copy(alpha = 0.5f),
                shape = RoundedCornerShape(1.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(
                color = if (pressed) GoldLight else CreamMuted,
            ),
        )
    }
}

@Composable
private fun CornerOrnament(
    modifier: Modifier = Modifier,
    flipX: Boolean = false,
    flipY: Boolean = false,
) {
    Canvas(modifier = modifier.size(52.dp)) {
        val canvasCenter = center
        val lineW = 1.4f
        val lineLen = 36.dp.toPx()
        val innerLen = 22.dp.toPx()
        val inset = 6.dp.toPx()
        val dotR = 2.5.dp.toPx()
        val smallDotR = 1.5f

        withTransform({
            scale(
                scaleX = if (flipX) -1f else 1f,
                scaleY = if (flipY) -1f else 1f,
                pivot = canvasCenter,
            )
        }) {
            // Primary outer L-brace
            drawLine(
                color = GoldPrimary,
                start = Offset(0f, 0f),
                end = Offset(lineLen, 0f),
                strokeWidth = lineW,
            )
            drawLine(
                color = GoldPrimary,
                start = Offset(0f, 0f),
                end = Offset(0f, lineLen),
                strokeWidth = lineW,
            )

            // Secondary inner L-brace
            drawLine(
                color = GoldMuted.copy(alpha = 0.6f),
                start = Offset(inset, inset),
                end = Offset(innerLen, inset),
                strokeWidth = lineW * 0.5f,
            )
            drawLine(
                color = GoldMuted.copy(alpha = 0.6f),
                start = Offset(inset, inset),
                end = Offset(inset, innerLen),
                strokeWidth = lineW * 0.5f,
            )

            // Corner anchor dot
            drawCircle(GoldLight, dotR, Offset(0f, 0f))

            // End nubs
            drawCircle(GoldMuted, smallDotR, Offset(lineLen, 0f))
            drawCircle(GoldMuted, smallDotR, Offset(0f, lineLen))

            // Inner corner dot
            drawCircle(GoldDark.copy(alpha = 0.8f), smallDotR * 0.8f, Offset(inset, inset))
        }
    }
}

@Composable
private fun DiceOverlay(diceState: DiceState, onResult: (Parity, Int) -> Unit, onDismiss: () -> Unit) {
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(diceState) {
        when (diceState) {
            is DiceState.Rolling -> overlayAlpha.animateTo(1f, tween(300))
            is DiceState.Idle -> overlayAlpha.animateTo(0f, tween(500))
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
                val guess = diceState.guess
                DiceRollingContent(
                    onFinished = { resultValue -> onResult(guess, resultValue) },
                )
            }
            is DiceState.Result -> {
                DiceResultContent(diceState = diceState, onTimeout = onDismiss)
            }
            is DiceState.Idle -> { /* fading out */ }
        }
    }
}

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

@Preview(showBackground = true)
@Composable
private fun LandingScreenPreview() {
    CardAppTheme {
        LandingScreen()
    }
}
