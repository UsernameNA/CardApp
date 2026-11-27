package com.github.username.cardapp.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.leatherBackground(): Modifier = this.drawWithCache {
    val leather = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to LeatherLight,
            0.45f to LeatherMid,
            1.0f to LeatherDeep,
        ),
        center = size.center,
        radius = size.maxDimension * 0.5f,
    )
    // Deep ink-black vignette at edges
    val vignette = Brush.radialGradient(
        colorStops = arrayOf(
            0.4f to Color.Transparent,
            1.0f to InkShadow,
        ),
        center = size.center,
        radius = size.maxDimension * 0.72f,
    )
    onDrawWithContent {
        drawRect(leather)
        drawContent()
        drawRect(vignette)
    }
}
