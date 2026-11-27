package com.github.username.cardapp.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography

/** Share of the screen height the scanned-cards panel takes at the bottom. */
internal const val SCANNED_PANEL_HEIGHT_FRACTION = 0.35f

@Composable
internal fun ScannedCardsPanel(
    cards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    modifier: Modifier = Modifier,
    prices: Map<String, Double> = emptyMap(),
    scanLogSize: Int = 0,
    cameraReady: Boolean = true,
    catalogueReady: Boolean = true,
    onCardClick: (String) -> Unit = {},
    onScanTap: () -> Unit = {},
    onToggleScanMode: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onExportScanLog: () -> Unit = {},
    lastOutcomeId: Long? = null,
    lastOutcomeCardName: String? = null,
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

            if (cameraReady) {
                Spacer(Modifier.width(12.dp))

                val autoActive = scanMode == ScanMode.Auto
                val autoColor = if (autoActive) GoldPrimary else GoldDark
                ScanPanelButton(
                    text = "AUTO",
                    onClick = onToggleScanMode,
                    highlighted = autoActive,
                    borderColor = autoColor,
                    textColor = autoColor,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                )

                Spacer(Modifier.width(8.dp))

                if (scanMode == ScanMode.Manual) {
                    val (buttonLabel, buttonColor) = when {
                        !catalogueReady -> "LOADING" to GoldMuted
                        scanStatus == ScanStatus.Scanning -> "..." to GoldMuted
                        scanStatus == ScanStatus.NotFound -> "NOT FOUND" to CreamFaded
                        scanStatus is ScanStatus.Found -> "FOUND!" to GoldLight
                        else -> "SCAN" to GoldPrimary
                    }
                    ScanPanelButton(
                        text = buttonLabel,
                        onClick = onScanTap,
                        enabled = catalogueReady && scanStatus != ScanStatus.Scanning,
                        textColor = buttonColor,
                    )
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
                        text = if (catalogueReady) statusLabel else "LOADING",
                        style = Typography.labelLarge.copy(color = statusColor),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
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
                    ScanPanelButton(
                        text = "ADD TO COLLECTION",
                        onClick = onAddToCollection,
                        modifier = Modifier.weight(1f),
                        borderColor = GoldPrimary,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    )
                }
                if (scanLogSize > 0) {
                    ScanPanelButton(
                        text = "EXPORT ($scanLogSize)",
                        onClick = onExportScanLog,
                        modifier = if (cards.isEmpty()) Modifier.fillMaxWidth() else Modifier,
                        textColor = GoldMuted,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Scanned cards list (newest first)
        val listState = rememberLazyListState()
        // Keyed on scans and corrections only — removing a card must not yank the list to
        // the top. The id, not the card, so scanning the same card again still scrolls.
        LaunchedEffect(lastOutcomeId, lastOutcomeCardName) {
            if (lastOutcomeCardName != null && cards.isNotEmpty()) listState.animateScrollToItem(0)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(cards, key = { it.card.name }) { entry ->
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
