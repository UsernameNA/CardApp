package com.github.username.cardapp.ui.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.BurgundyBright
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun DiceResultContent(diceState: DiceState.Result, onTimeout: () -> Unit) {
    val isCorrect = (diceState.value % 2 == 1) == (diceState.guess == Parity.ODD)
    val verdictColor = if (isCorrect) GoldPrimary else BurgundyBright

    val popScale = remember { Animatable(0.6f) }
    LaunchedEffect(diceState) {
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    LaunchedEffect(diceState) {
        delay(3500.milliseconds)
        onTimeout()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .scale(popScale.value)
            .drawBehind {
                val glowRadius = size.maxDimension * 0.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(verdictColor.copy(alpha = 0.35f), Color.Transparent),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
            },
    ) {
        Text(
            text = "${diceState.value}",
            style = Typography.displayLarge.copy(
                color = GoldLight,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(12.dp))
        VerdictBanner(
            text = if (isCorrect) "CORRECT!" else "WRONG!",
            color = verdictColor,
            textColor = if (isCorrect) LeatherDeep else CreamPrimary,
        )
    }
}

@Composable
private fun VerdictBanner(text: String, color: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val hairlineH = 1.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.15f to color.copy(alpha = 0.85f),
                        0.5f to color,
                        0.85f to color.copy(alpha = 0.85f),
                        1f to Color.Transparent,
                    ),
                )
                val hairline = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.2f to GoldLight,
                    0.8f to GoldLight,
                    1f to Color.Transparent,
                )
                drawRect(hairline, size = Size(size.width, hairlineH))
                drawRect(
                    hairline,
                    topLeft = Offset(0f, size.height - hairlineH),
                    size = Size(size.width, hairlineH),
                )
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = Typography.displayMedium.copy(
                color = textColor,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
