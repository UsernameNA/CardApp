package com.github.username.cardapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.elementColor

@Composable
fun AlchemicalSymbol(element: String, fontSize: TextUnit = 10.sp) {
    val symbol = when (element.lowercase()) {
        "air"   -> "\uD83D\uDF01" // 🜁 U+1F701
        "fire"  -> "\uD83D\uDF02" // 🜂 U+1F702
        "earth" -> "\uD83D\uDF03" // 🜃 U+1F703
        "water" -> "\uD83D\uDF04" // 🜄 U+1F704
        else -> return
    }
    Text(
        text = symbol,
        style = Typography.labelSmall.copy(
            color = elementColor(element),
            fontSize = fontSize,
            lineHeight = fontSize,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

/**
 * Arranges element threshold symbols in a 2-row grid:
 * - 1 symbol: single symbol at [singleSize]
 * - 2 symbols: one top, one bottom
 * - 3 symbols: one top, two bottom (like elements on same row)
 * - 4 symbols: two top, two bottom (like elements on same row)
 */
@Composable
fun ElementThresholdGrid(
    thresholds: List<Pair<String, Int>>,
    singleSize: TextUnit = 16.sp,
    gridSize: TextUnit = 11.sp,
) {
    val total = thresholds.sumOf { it.second }
    if (total == 0) return

    if (total == 1) {
        val (element, _) = thresholds.first()
        AlchemicalSymbol(element, singleSize)
        return
    }

    val (topGroups, bottomGroups) = splitForRows(thresholds)
    val overlapDp = with(LocalDensity.current) { (gridSize * 0.5f).toDp() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-overlapDp),
    ) {
        SymbolRow(topGroups, gridSize)
        SymbolRow(bottomGroups, gridSize)
    }
}

@Composable
private fun SymbolRow(groups: List<Pair<String, Int>>, fontSize: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        groups.forEach { (element, count) ->
            repeat(count) {
                AlchemicalSymbol(element, fontSize)
            }
        }
    }
}

private fun splitForRows(
    thresholds: List<Pair<String, Int>>,
): Pair<List<Pair<String, Int>>, List<Pair<String, Int>>> {
    val total = thresholds.sumOf { it.second }
    val topTarget = total / 2

    // Single element type — split the count across rows
    if (thresholds.size == 1) {
        val (el, count) = thresholds[0]
        return listOf(el to topTarget) to listOf(el to (count - topTarget))
    }

    // Multiple element types — keep like elements together, fill top row first
    val sorted = thresholds.sortedBy { it.second }
    val top = mutableListOf<Pair<String, Int>>()
    val bottom = mutableListOf<Pair<String, Int>>()
    var filled = 0

    for (group in sorted) {
        if (filled + group.second <= topTarget) {
            top.add(group)
            filled += group.second
        } else {
            bottom.add(group)
        }
    }

    // If top ended up empty (all groups too large), split the first bottom group
    if (top.isEmpty() && bottom.isNotEmpty()) {
        val first = bottom.removeFirst()
        top.add(first.first to topTarget)
        bottom.add(0, first.first to (first.second - topTarget))
    }

    return top to bottom
}
