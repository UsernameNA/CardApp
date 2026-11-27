package com.github.username.cardapp.ui.landing

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.R
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.drawDiamond
import com.github.username.cardapp.ui.theme.leatherBackground

@Composable
fun LandingScreen(
    onViewCards: () -> Unit = {},
    onViewCollection: () -> Unit = {},
    onScanCards: () -> Unit = {},
    onTrackGame: () -> Unit = {},
) {
    var diceState by remember { mutableStateOf<DiceState>(DiceState.Idle) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        // Gilded corner ornaments
        CornerOrnament(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            flipX = false, flipY = false,
        )
        CornerOrnament(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            flipX = true, flipY = false,
        )
        CornerOrnament(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            flipX = false, flipY = true,
        )
        CornerOrnament(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            flipX = true, flipY = true,
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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
            ArcaneButton(text = "TRACK GAME", onClick = onTrackGame, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArcaneButton(
                    text = "ODD",
                    onClick = { if (diceState is DiceState.Idle) diceState = DiceState.Rolling(Parity.ODD) },
                    modifier = Modifier.weight(1f),
                    accent = true,
                )
                ArcaneButton(
                    text = "EVEN",
                    onClick = { if (diceState is DiceState.Idle) diceState = DiceState.Rolling(Parity.EVEN) },
                    modifier = Modifier.weight(1f),
                    accent = true,
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

private val ShimmerColors = listOf(
    GoldMuted,
    GoldMuted,
    GoldPrimary,
    GoldLight,
    GoldPrimary,
    GoldMuted,
    GoldMuted,
)

private fun shimmerBrush(shimmerX: Float): Brush = Brush.linearGradient(
    colors = ShimmerColors,
    start = Offset(shimmerX - 200f, 0f),
    end = Offset(shimmerX + 200f, 0f),
)

@Composable
private fun TitleSection() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    // Read only while drawing, so the sweep redraws each frame without recomposing
    val shimmerX = shimmerTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CARDAPP",
            style = Typography.displayLarge.copy(
                color = GoldMuted,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = shimmerBrush(shimmerX.value), blendMode = BlendMode.SrcIn)
                },
        )
        // Raven landing on the "D" — shimmer brush applied via SrcIn blend
        val ravenPainter = painterResource(R.drawable.ic_launcher_foreground)
        Spacer(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 18.dp, y = (-58).dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawBehind {
                    with(ravenPainter) {
                        draw(size = Size(size.width, size.height))
                    }
                    drawRect(brush = shimmerBrush(shimmerX.value), blendMode = BlendMode.SrcIn)
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
        drawDiamond(center = Offset(halfW, midY), halfHeight = diamondR, color = GoldPrimary)

        // Flanking dots
        drawCircle(GoldMuted.copy(alpha = 0.8f), dotR, Offset(halfW - dotGap, midY))
        drawCircle(GoldMuted.copy(alpha = 0.8f), dotR, Offset(halfW + dotGap, midY))
    }
}

@Composable
private fun ArcaneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "btnScale",
    )

    val outerBorder = when {
        accent -> if (pressed) GoldLight else GoldPrimary
        else -> if (pressed) GoldPrimary else GoldMuted
    }
    val fill = when {
        accent -> (if (pressed) GoldDark.copy(alpha = 0.6f) else GoldDark.copy(alpha = 0.4f)).compositeOver(LeatherMid)
        else -> if (pressed) LeatherMid else LeatherMid.copy(alpha = 0.5f)
    }
    val textColor = when {
        accent -> if (pressed) CreamPrimary else GoldLight
        else -> if (pressed) GoldLight else CreamMuted
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .border(
                width = if (accent) 1.dp else 0.8.dp,
                color = outerBorder,
                shape = RoundedCornerShape(2.dp),
            )
            .padding(2.dp)
            .border(
                width = 0.5.dp,
                color = if (accent) GoldMuted.copy(alpha = 0.7f) else GoldDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(1.dp),
            )
            .background(
                color = fill,
                shape = RoundedCornerShape(1.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Typography.labelLarge.copy(
                color = textColor,
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

@Preview(showBackground = true)
@Composable
private fun LandingScreenPreview() {
    CardAppTheme {
        LandingScreen()
    }
}
