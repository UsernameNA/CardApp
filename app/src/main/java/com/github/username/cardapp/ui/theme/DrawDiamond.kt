package com.github.username.cardapp.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill

/**
 * The codex's lozenge ornament: a vertical diamond [halfHeight] tall above and below [center],
 * [widthRatio] times as wide. Pass `Stroke(width)` as [style] for an outline.
 */
fun DrawScope.drawDiamond(
    center: Offset,
    halfHeight: Float,
    color: Color,
    style: DrawStyle = Fill,
    widthRatio: Float = 0.65f,
) {
    val halfWidth = halfHeight * widthRatio
    val path = Path().apply {
        moveTo(center.x, center.y - halfHeight)
        lineTo(center.x + halfWidth, center.y)
        lineTo(center.x, center.y + halfHeight)
        lineTo(center.x - halfWidth, center.y)
        close()
    }
    drawPath(path, color = color, style = style)
}
