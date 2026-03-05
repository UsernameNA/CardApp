package com.github.username.cardapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.leatherBackground(): Modifier = this
    .background(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to LeatherLight,
                0.45f to LeatherMid,
                1.0f to LeatherDeep,
            ),
            radius = 1200f,
        )
    )
    .drawWithContent {
        drawContent()
        // Deep ink-black vignette at edges
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.4f to Color.Transparent,
                    1.0f to InkShadow,
                ),
                radius = maxOf(size.width, size.height) * 0.72f,
                center = center,
            )
        )
    }
