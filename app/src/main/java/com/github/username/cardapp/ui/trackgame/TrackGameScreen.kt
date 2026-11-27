package com.github.username.cardapp.ui.trackgame

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.drawDiamond
import com.github.username.cardapp.ui.theme.leatherBackground
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val STARTING_LIFE = 20
private const val MAX_LIFE = 20
private const val MIN_LIFE = 0
private const val DELTA_TIMEOUT_MS = 2000L
private const val RESET_CONFIRM_MS = 3000L

private val LifeTextStyle = TextStyle(
    color = GoldLight,
    textAlign = TextAlign.Center,
    fontSize = 230.sp,
    // em, not sp: auto-sizing scales only fontSize, and these must shrink with it
    lineHeight = (220f / 230f).em,
    letterSpacing = (4f / 230f).em,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
)

@Composable
fun TrackGameScreen() {
    // Saveable: a phone lying on the table rotates easily, and that must not reset the game.
    var uncapped by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var player1Life by rememberSaveable { mutableIntStateOf(STARTING_LIFE) }
    var player2Life by rememberSaveable { mutableIntStateOf(STARTING_LIFE) }
    var player1Delta by rememberSaveable { mutableIntStateOf(0) }
    var player2Delta by rememberSaveable { mutableIntStateOf(0) }

    fun reset() {
        player1Life = STARTING_LIFE
        player2Life = STARTING_LIFE
        player1Delta = 0
        player2Delta = 0
    }

    val toggleUncapped = {
        uncapped = !uncapped
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground()
            .keepScreenOn()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PlayerHalf(
            player = "Player 2",
            life = player2Life,
            delta = player2Delta,
            uncapped = uncapped,
            onGain = {
                if (uncapped || player2Life < MAX_LIFE) {
                    player2Life++
                    player2Delta++
                }
            },
            onLose = {
                if (player2Life > MIN_LIFE) {
                    player2Life--
                    player2Delta--
                }
            },
            onToggleUncapped = toggleUncapped,
            onDeltaCleared = { player2Delta = 0 },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .rotate(180f),
        )

        ResetDivider(onReset = ::reset, uncapped = uncapped)

        PlayerHalf(
            player = "Player 1",
            life = player1Life,
            delta = player1Delta,
            uncapped = uncapped,
            onGain = {
                if (uncapped || player1Life < MAX_LIFE) {
                    player1Life++
                    player1Delta++
                }
            },
            onLose = {
                if (player1Life > MIN_LIFE) {
                    player1Life--
                    player1Delta--
                }
            },
            onToggleUncapped = toggleUncapped,
            onDeltaCleared = { player1Delta = 0 },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

/** Taps on the upper half of the zone, as its player faces it, gain a life; the lower half loses one. */
@Composable
private fun PlayerHalf(
    player: String,
    life: Int,
    delta: Int,
    uncapped: Boolean,
    onGain: () -> Unit,
    onLose: () -> Unit,
    onToggleUncapped: () -> Unit,
    onDeltaCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnGain by rememberUpdatedState(onGain)
    val currentOnLose by rememberUpdatedState(onLose)
    val currentOnToggleUncapped by rememberUpdatedState(onToggleUncapped)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { currentOnToggleUncapped() }) { offset ->
                    if (offset.y < size.height / 2) currentOnGain() else currentOnLose()
                }
            }
            .clearAndSetSemantics {
                contentDescription = "$player life"
                stateDescription = if (life <= MIN_LIFE) "Defeated" else "$life"
                customActions = listOf(
                    CustomAccessibilityAction("Gain 1 life") { currentOnGain(); true },
                    CustomAccessibilityAction("Lose 1 life") { currentOnLose(); true },
                    CustomAccessibilityAction(if (uncapped) "Restore life cap" else "Remove life cap") {
                        currentOnToggleUncapped(); true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LifeDisplay(life = life, delta = delta, onDeltaCleared = onDeltaCleared)
    }
}

@Composable
private fun LifeDisplay(life: Int, delta: Int, onDeltaCleared: () -> Unit = {}) {
    val deltaAlpha = remember { Animatable(0f) }

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
            BasicText(
                text = "$life",
                style = LifeTextStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 24.sp,
                    maxFontSize = LifeTextStyle.fontSize,
                    stepSize = 2.sp,
                ),
            )
        }
        if (delta != 0) {
            Text(
                text = if (delta > 0) "+$delta" else "$delta",
                style = Typography.titleMedium.copy(
                    color = if (delta > 0) GoldPrimary else BurgundyAccent,
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
    val request = remember(context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/dd.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = "Defeated",
        modifier = Modifier
            .sizeIn(maxWidth = 400.dp, maxHeight = 400.dp)
            .fillMaxSize(),
        colorFilter = ColorFilter.tint(GoldPrimary),
    )
}

@Composable
private fun ResetDivider(onReset: () -> Unit, uncapped: Boolean) {
    // Reset wipes both totals, so the first tap only arms it
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(RESET_CONFIRM_MS.milliseconds)
            armed = false
        }
    }

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
                drawDiamond(
                    center = Offset(halfW + sign * diamondOffset, midY),
                    halfHeight = diamondR,
                    color = GoldPrimary,
                )
            }

            // Edge dots
            val edgeDotOffset = textGap + 18.dp.toPx()
            drawCircle(GoldMuted.copy(alpha = 0.7f), dotR, Offset(halfW - edgeDotOffset, midY))
            drawCircle(GoldMuted.copy(alpha = 0.7f), dotR, Offset(halfW + edgeDotOffset, midY))

            // Outer edge dots
            drawCircle(GoldDark.copy(alpha = 0.5f), dotR * 0.7f, Offset(4.dp.toPx(), midY))
            drawCircle(GoldDark.copy(alpha = 0.5f), dotR * 0.7f, Offset(size.width - 4.dp.toPx(), midY))
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (armed) "Confirm reset of both life totals" else "Reset life totals",
                ) {
                    if (armed) {
                        armed = false
                        onReset()
                    } else {
                        armed = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    armed -> "SURE?"
                    uncapped -> "mtg"
                    else -> "RESET"
                },
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "dividerLabel",
            ) { label ->
                Text(
                    text = label,
                    style = Typography.labelMedium.copy(
                        color = if (label == "SURE?") GoldLight else GoldMuted,
                        textAlign = TextAlign.Center,
                        letterSpacing = 3.sp,
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackGameScreenPreview() {
    CardAppTheme {
        TrackGameScreen()
    }
}

