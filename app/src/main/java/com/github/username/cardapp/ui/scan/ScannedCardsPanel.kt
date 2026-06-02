package com.github.username.cardapp.ui.scan

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography

@Composable
internal fun ScannedCardsPanel(
    cards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    modifier: Modifier = Modifier,
    prices: Map<String, Double> = emptyMap(),
    scanLogSize: Int = 0,
    onCardClick: (String) -> Unit = {},
    onScanTap: () -> Unit = {},
    onToggleScanMode: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onExportScanLog: () -> Unit = {},
    lastScanCardName: String? = null,
) {
    var selectedCardName by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .background(LeatherMid.copy(alpha = 0.93f))
            .drawWithContent {
                drawContent()
                drawLine(
                    GoldMuted.copy(alpha = 0.6f),
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    1.dp.toPx(),
                )
            },
    ) {
        // Panel header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
            Text(
                text = "  SCANNED  ",
                style = Typography.labelLarge.copy(color = GoldPrimary),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
            Spacer(Modifier.width(12.dp))

            // AUTO mode toggle
            val autoActive = scanMode == ScanMode.Auto
            val autoInteraction = remember { MutableInteractionSource() }
            val autoPressed by autoInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .border(
                        1.dp,
                        if (autoPressed) GoldLight else if (autoActive) GoldPrimary else GoldDark,
                        RoundedCornerShape(2.dp),
                    )
                    .background(if (autoPressed || autoActive) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable(
                        interactionSource = autoInteraction,
                        indication = null,
                        onClick = onToggleScanMode,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "AUTO",
                    style = Typography.labelLarge.copy(
                        color = if (autoPressed) GoldLight else if (autoActive) GoldPrimary else GoldDark,
                    ),
                )
            }

            Spacer(Modifier.width(8.dp))

            if (scanMode == ScanMode.Manual) {
                // Manual mode: normal SCAN button
                val scanInteraction = remember { MutableInteractionSource() }
                val scanPressed by scanInteraction.collectIsPressedAsState()
                val scanBusy = scanStatus == ScanStatus.Scanning
                val (buttonLabel, buttonColor) = when (scanStatus) {
                    ScanStatus.Idle -> "SCAN" to GoldPrimary
                    ScanStatus.Scanning -> "..." to GoldMuted
                    ScanStatus.NotFound -> "NOT FOUND" to CreamFaded
                    is ScanStatus.Found -> "FOUND!" to GoldLight
                    else -> "SCAN" to GoldPrimary
                }
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (scanPressed) GoldLight else GoldMuted,
                            RoundedCornerShape(2.dp)
                        )
                        .background(if (scanPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable(
                            interactionSource = scanInteraction,
                            indication = null,
                            enabled = !scanBusy,
                            onClick = onScanTap,
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        buttonLabel,
                        style = Typography.labelLarge.copy(color = if (scanPressed) GoldLight else buttonColor)
                    )
                }
            } else {
                // Auto mode: show current watch status
                val (statusLabel, statusColor) = when (scanStatus) {
                    ScanStatus.AutoWatching -> "WATCHING" to GoldMuted
                    ScanStatus.Scanning -> "SCANNING..." to GoldPrimary
                    ScanStatus.AutoCooldown -> "NEXT..." to CreamFaded
                    is ScanStatus.Found -> "FOUND!" to GoldLight
                    else -> "WATCHING" to GoldMuted
                }
                Text(
                    text = statusLabel,
                    style = Typography.labelLarge.copy(color = statusColor),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }

        // Action buttons row
        if (cards.isNotEmpty() || scanLogSize > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cards.isNotEmpty()) {
                    val addInteraction = remember { MutableInteractionSource() }
                    val addPressed by addInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (addPressed) GoldLight else GoldPrimary,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (addPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable(
                                interactionSource = addInteraction,
                                indication = null,
                                onClick = onAddToCollection,
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "ADD TO COLLECTION",
                            style = Typography.labelLarge.copy(
                                color = if (addPressed) GoldLight else GoldPrimary,
                            ),
                        )
                    }
                }
                if (scanLogSize > 0) {
                    val exportInteraction = remember { MutableInteractionSource() }
                    val exportPressed by exportInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .then(if (cards.isEmpty()) Modifier.fillMaxWidth() else Modifier)
                            .border(
                                1.dp,
                                if (exportPressed) GoldLight else GoldMuted,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (exportPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable(
                                interactionSource = exportInteraction,
                                indication = null,
                                onClick = onExportScanLog,
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "EXPORT ($scanLogSize)",
                            style = Typography.labelLarge.copy(
                                color = if (exportPressed) GoldLight else GoldMuted,
                            ),
                        )
                    }
                }
            }
        }

        // Scanned cards list (newest first)
        val listState = rememberLazyListState()
        LaunchedEffect(lastScanCardName, cards.size) {
            if (cards.isNotEmpty()) listState.animateScrollToItem(0)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(cards, key = { _, entry -> entry.card.name }) { _, entry ->
                CardRow(
                    card = entry.card,
                    count = entry.count,
                    isSelected = selectedCardName == entry.card.name,
                    onToggle = {
                        selectedCardName =
                            if (selectedCardName == entry.card.name) null else entry.card.name
                    },
                    onLongPress = { onCardClick(entry.card.name) },
                    onIncrement = { onIncrement(entry.card.name) },
                    onDecrement = {
                        if (entry.count <= 1) selectedCardName = null
                        onDecrement(entry.card.name)
                    },
                    marketPrice = prices[entry.card.name],
                )
                HorizontalDivider(color = GoldDark.copy(alpha = 0.25f), thickness = 0.5.dp)
            }
        }
    }
}
