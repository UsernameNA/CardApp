package com.github.username.cardapp.ui.trackgame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ColorFilter
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground

private const val STARTING_LIFE = 20
private const val MAX_LIFE = 20
private const val MIN_LIFE = 0
private const val DELTA_TIMEOUT_MS = 2000L

@Composable
fun TrackGameScreen() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var player1Life by remember { mutableIntStateOf(STARTING_LIFE) }
    var player2Life by remember { mutableIntStateOf(STARTING_LIFE) }
    var player1Delta by remember { mutableIntStateOf(0) }
    var player2Delta by remember { mutableIntStateOf(0) }
    // Delta version counters — each tap increments the version, which restarts
    // the fade animation in LifeDisplay. When the fade completes, delta is cleared.
    var player1DeltaVersion by remember { mutableIntStateOf(0) }
    var player2DeltaVersion by remember { mutableIntStateOf(0) }

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
                            if (player2Life > MIN_LIFE) {
                                player2Life--
                                player2Delta--
                                player2DeltaVersion++
                            }
                        } else {
                            if (player2Life < MAX_LIFE) {
                                player2Life++
                                player2Delta++
                                player2DeltaVersion++
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LifeDisplay(life = player2Life, delta = player2Delta, onDeltaCleared = { player2Delta = 0 })
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
                            if (player1Life < MAX_LIFE) {
                                player1Life++
                                player1Delta++
                                player1DeltaVersion++
                            }
                        } else {
                            if (player1Life > MIN_LIFE) {
                                player1Life--
                                player1Delta--
                                player1DeltaVersion++
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LifeDisplay(life = player1Life, delta = player1Delta, onDeltaCleared = { player1Delta = 0 })
        }
    }
}

@Composable
private fun LifeDisplay(life: Int, delta: Int, onDeltaCleared: () -> Unit = {}) {
    val deltaAlpha = remember { Animatable(0f) }
    var displayDelta by remember { mutableIntStateOf(0) }
    if (delta != 0) displayDelta = delta

    LaunchedEffect(delta) {
        if (delta != 0) {
            deltaAlpha.snapTo(1f)
            deltaAlpha.animateTo(0f, tween(DELTA_TIMEOUT_MS.toInt(), easing = FastOutLinearInEasing))
            onDeltaCleared()
        } else {
            deltaAlpha.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (life <= MIN_LIFE) {
            DeadImage()
        } else {
            Text(
                text = "$life",
                style = Typography.displayLarge.copy(
                    color = GoldLight,
                    textAlign = TextAlign.Center,
                    fontSize = 230.sp,
                    lineHeight = 220.sp,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        if (deltaAlpha.value > 0f) {
            val text = if (displayDelta > 0) "+$displayDelta" else "$displayDelta"
            Text(
                text = text,
                style = Typography.titleMedium.copy(
                    color = if (displayDelta > 0) GoldPrimary else BurgundyAccent,
                    fontSize = 30.sp,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 40.dp)
                    .graphicsLayer { alpha = deltaAlpha.value },
            )
        }
    }
}

@Composable
private fun DeadImage() {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/dd.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = "Defeated",
        modifier = Modifier.size(400.dp),
        colorFilter = ColorFilter.tint(GoldPrimary),
    )
}

@Composable
private fun ResetDivider(onReset: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 20.dp),
        ) {
            val midY = size.height / 2f
            val halfW = size.width / 2f
            val lineAlpha = 0.5f
            val diamondR = 4.dp.toPx()
            val dotR = 1.8.dp.toPx()

            // Gap for RESET text (~60dp)
            val textGap = 80.dp.toPx() / 2f

            // Main lines from edges to text gap
            drawLine(
                color = GoldMuted.copy(alpha = lineAlpha),
                start = Offset(0f, midY),
                end = Offset(halfW - textGap, midY),
                strokeWidth = 2f,
            )
            drawLine(
                color = GoldMuted.copy(alpha = lineAlpha),
                start = Offset(halfW + textGap, midY),
                end = Offset(size.width, midY),
                strokeWidth = 2f,
            )

            // Inner accent lines (slightly offset vertically)
            val innerInset = 24.dp.toPx()
            val accentOffset = 4.dp.toPx()
            drawLine(
                color = GoldDark.copy(alpha = 0.35f),
                start = Offset(innerInset, midY - accentOffset),
                end = Offset(halfW - textGap - 8.dp.toPx(), midY - accentOffset),
                strokeWidth = 0.4f,
            )
            drawLine(
                color = GoldDark.copy(alpha = 0.35f),
                start = Offset(halfW + textGap + 8.dp.toPx(), midY - accentOffset),
                end = Offset(size.width - innerInset, midY - accentOffset),
                strokeWidth = 0.4f,
            )
            drawLine(
                color = GoldDark.copy(alpha = 0.35f),
                start = Offset(innerInset, midY + accentOffset),
                end = Offset(halfW - textGap - 8.dp.toPx(), midY + accentOffset),
                strokeWidth = 0.4f,
            )
            drawLine(
                color = GoldDark.copy(alpha = 0.35f),
                start = Offset(halfW + textGap + 8.dp.toPx(), midY + accentOffset),
                end = Offset(size.width - innerInset, midY + accentOffset),
                strokeWidth = 0.4f,
            )

            // Flanking diamonds
            val diamondOffset = textGap + 6.dp.toPx()
            for (sign in listOf(-1f, 1f)) {
                val cx = halfW + sign * diamondOffset
                val path = Path().apply {
                    moveTo(cx, midY - diamondR)
                    lineTo(cx + diamondR * 0.65f, midY)
                    lineTo(cx, midY + diamondR)
                    lineTo(cx - diamondR * 0.65f, midY)
                    close()
                }
                drawPath(path, color = GoldPrimary)
            }

            // Edge dots
            val edgeDotOffset = textGap + 18.dp.toPx()
            drawCircle(GoldMuted.copy(alpha = 0.7f), dotR, Offset(halfW - edgeDotOffset, midY))
            drawCircle(GoldMuted.copy(alpha = 0.7f), dotR, Offset(halfW + edgeDotOffset, midY))

            // Outer edge dots
            drawCircle(GoldDark.copy(alpha = 0.5f), dotR * 0.7f, Offset(4.dp.toPx(), midY))
            drawCircle(GoldDark.copy(alpha = 0.5f), dotR * 0.7f, Offset(size.width - 4.dp.toPx(), midY))
        }
        Text(
            text = "RESET",
            style = Typography.labelMedium.copy(
                color = GoldMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp,
            ),
            modifier = Modifier.pointerInput(Unit) { detectTapGestures { onReset() } },
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

