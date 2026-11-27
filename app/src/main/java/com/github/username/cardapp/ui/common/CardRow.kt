package com.github.username.cardapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.rarityColor

@Composable
fun CardRow(
    card: CardEntity,
    count: Int,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    marketPrice: Double? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) GoldDark.copy(alpha = 0.18f) else Color.Transparent)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = onToggle,
                        onLongClick = onLongPress,
                    )
                } else {
                    Modifier.clickable(onClick = onToggle)
                },
            )
            .padding(horizontal = 16.dp, vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(rarityColor(card.rarity).copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.name.uppercase(),
                style = Typography.labelMedium.copy(color = CreamPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CardInfoLine(card)
        }
        if (marketPrice != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = PriceFormat.compact(marketPrice),
                style = Typography.labelMedium.copy(color = CreamMuted),
                modifier = Modifier.widthIn(min = 48.dp),
                textAlign = TextAlign.End,
            )
        }
        if (isSelected) {
            // The buttons' touch-target margin spaces them from the price.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.overflowVertically(ROW_VERTICAL_PADDING),
            ) {
                CountButton("\u25bc", contentDescription = "Remove one", onClick = onDecrement)
                Text(
                    text = "$count",
                    style = Typography.labelLarge.copy(color = GoldPrimary),
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                CountButton("\u25b2", contentDescription = "Add one", onClick = onIncrement)
            }
        } else {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (count > 1) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, GoldMuted.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            .clearAndSetSemantics { contentDescription = countOf(count, "copy", "copies") }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u00d7$count",
                            style = Typography.labelSmall.copy(color = GoldPrimary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoLine(card: CardEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = card.cardType,
            style = Typography.bodyMedium.copy(color = CreamFaded),
        )
        val isSite = card.cardType.equals("Site", ignoreCase = true)
        val isAvatar = card.cardType.equals("Avatar", ignoreCase = true)
        val thresholds = buildList {
            if (card.airThreshold > 0) add("air" to card.airThreshold)
            if (card.earthThreshold > 0) add("earth" to card.earthThreshold)
            if (card.fireThreshold > 0) add("fire" to card.fireThreshold)
            if (card.waterThreshold > 0) add("water" to card.waterThreshold)
        }
        if (!isSite || thresholds.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            if (!isSite && !isAvatar) {
                Text(
                    text = "${card.cost}",
                    style = Typography.bodyLarge.copy(color = CreamPrimary),
                )
                Spacer(Modifier.width(4.dp))
            }
            ElementThresholdGrid(
                thresholds = thresholds,
                singleSize = 16.sp,
                gridSize = 11.sp,
            )
        }
    }
}

private val ROW_VERTICAL_PADDING = 8.dp

/** Lets content (48dp touch targets) spill [amount] into the row's padding above and below, so the row keeps its height. */
private fun Modifier.overflowVertically(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val inset = amount.roundToPx().coerceAtMost(placeable.height / 2)
    layout(placeable.width, placeable.height - 2 * inset) { placeable.place(0, -inset) }
}

@Composable
private fun CountButton(label: String, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(26.dp)
            .border(1.dp, GoldMuted, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Typography.labelMedium.copy(color = GoldPrimary),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
