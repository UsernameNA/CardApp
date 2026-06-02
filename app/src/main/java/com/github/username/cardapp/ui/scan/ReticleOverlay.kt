package com.github.username.cardapp.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary

@Composable
internal fun ReticleOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "reticle")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val panelFraction = 0.35f
        val availH = size.height * (1f - panelFraction)
        val cardW = size.width * 0.9f
        val cardH = cardW / 0.72f
        val cx = size.width / 2f
        val cy = availH / 2f

        val left = cx - cardW / 2f
        val top = cy - cardH / 4f
        val right = cx + cardW / 2f
        val bracketLen = cardW * 0.18f
        val strokePx = 2.dp.toPx()
        val thinStroke = strokePx * 0.5f
        val alpha = 0.55f + pulse * 0.45f
        val goldColor = GoldPrimary.copy(alpha = alpha)
        val goldLight = GoldLight.copy(alpha = alpha * 0.85f)
        val goldFaint = GoldMuted.copy(alpha = alpha * 0.4f)
        val dSize = 4.dp.toPx()
        val tickLen = 6.dp.toPx()
        val inset = 8.dp.toPx()

        // ── Primary L-brackets ──
        // Top-left
        drawLine(goldColor, Offset(left, top + bracketLen), Offset(left, top), strokePx)
        drawLine(goldColor, Offset(left, top), Offset(left + bracketLen, top), strokePx)
        // Top-right
        drawLine(goldColor, Offset(right - bracketLen, top), Offset(right, top), strokePx)
        drawLine(goldColor, Offset(right, top), Offset(right, top + bracketLen), strokePx)

        // ── Inner accent L-brackets (thinner, inset) ──
        drawLine(
            goldFaint,
            Offset(left + inset, top + inset),
            Offset(left + inset, top + inset + bracketLen * 0.5f),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(left + inset, top + inset),
            Offset(left + inset + bracketLen * 0.5f, top + inset),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(right - inset, top + inset),
            Offset(right - inset, top + inset + bracketLen * 0.5f),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(right - inset, top + inset),
            Offset(right - inset - bracketLen * 0.5f, top + inset),
            thinStroke
        )

        // ── Connecting line between brackets (faint horizontal rule) ──
        drawLine(
            goldFaint,
            Offset(left + bracketLen + dSize * 2, top),
            Offset(right - bracketLen - dSize * 2, top),
            thinStroke
        )

        // ── Corner diamonds ──
        listOf(Offset(left, top), Offset(right, top)).forEach { corner ->
            val diamondPath = Path().apply {
                moveTo(corner.x, corner.y - dSize)
                lineTo(corner.x + dSize * 0.65f, corner.y)
                lineTo(corner.x, corner.y + dSize)
                lineTo(corner.x - dSize * 0.65f, corner.y)
                close()
            }
            drawPath(diamondPath, goldLight, style = Stroke(strokePx * 0.8f))
        }

        // ── Center diamond on the connecting line ──
        val centerDiamond = Path().apply {
            val ds = dSize * 0.7f
            moveTo(cx, top - ds)
            lineTo(cx + ds * 0.65f, top)
            lineTo(cx, top + ds)
            lineTo(cx - ds * 0.65f, top)
            close()
        }
        drawPath(centerDiamond, goldColor)

        // ── Flanking tick marks along connecting line ──
        val tickPositions = listOf(0.25f, 0.4f, 0.6f, 0.75f)
        for (frac in tickPositions) {
            val tx = left + (right - left) * frac
            drawLine(
                goldFaint,
                Offset(tx, top - tickLen * 0.5f),
                Offset(tx, top + tickLen * 0.5f),
                thinStroke
            )
        }

        // ── Bracket end nubs ──
        val nubR = 1.8f
        drawCircle(goldLight, nubR, Offset(left + bracketLen, top))
        drawCircle(goldLight, nubR, Offset(left, top + bracketLen))
        drawCircle(goldLight, nubR, Offset(right - bracketLen, top))
        drawCircle(goldLight, nubR, Offset(right, top + bracketLen))

        // ── Center aim dot ──
        drawCircle(GoldLight.copy(alpha = alpha * 0.6f), 2.5.dp.toPx(), Offset(cx, cy * 1.5f))
    }
}
