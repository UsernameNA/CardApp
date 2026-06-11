package com.github.username.cardapp.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ScanResultOverlay(
    lastScanCard: CardEntity?,
    unmatchedScan: Boolean,
    top3: List<ScanCandidate>,
    correctionResults: List<CardEntity>,
    onCorrect: (String) -> Unit,
    onCorrectionOpenChanged: (Boolean) -> Unit,
    onCorrectionQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track whether a scan result is active (matched or unmatched)
    val hasResult = lastScanCard != null || unmatchedScan
    var correctionExpanded by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Each new scan: pop in, auto-fade after delay
    LaunchedEffect(lastScanCard, unmatchedScan) {
        if (hasResult) {
            correctionExpanded = false
            onCorrectionOpenChanged(false)
            overlayAlpha.snapTo(1f)
            delay(3000)
            if (!correctionExpanded) {
                overlayAlpha.animateTo(0f, tween(500))
            }
        } else {
            overlayAlpha.snapTo(0f)
        }
    }

    // Leave the composition whenever fully faded — an invisible overlay must not
    // keep intercepting taps (its clickable would silently toggle the correction panel).
    if (overlayAlpha.value == 0f) return

    val overlayInteraction = remember { MutableInteractionSource() }
    val overlayPressed by overlayInteraction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(
                if (overlayPressed) LeatherMid.copy(alpha = 0.95f)
                else LeatherDeep.copy(alpha = 0.9f),
                RoundedCornerShape(4.dp),
            )
            .border(
                0.5.dp,
                if (overlayPressed) GoldMuted else GoldDark.copy(alpha = 0.4f),
                RoundedCornerShape(4.dp),
            )
            .clickable(
                interactionSource = overlayInteraction,
                indication = null,
            ) {
                correctionExpanded = !correctionExpanded
                onCorrectionOpenChanged(correctionExpanded)
                if (correctionExpanded) {
                    scope.launch { overlayAlpha.snapTo(1f) }
                }
            }
            .padding(10.dp),
    ) {
        if (lastScanCard != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = "file:///android_asset/images/${lastScanCard.primarySlug}.webp",
                    contentDescription = lastScanCard.name,
                    modifier = Modifier
                        .size(width = 48.dp, height = 67.dp)
                        .border(0.5.dp, GoldDark, RoundedCornerShape(2.dp)),
                )
                Column {
                    Text(
                        text = lastScanCard.name.uppercase(),
                        style = Typography.labelMedium.copy(color = CreamPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Tap to correct",
                        style = Typography.labelSmall.copy(color = GoldMuted),
                    )
                }
            }
        } else {
            Text(
                text = "NO MATCH — tap to correct",
                style = Typography.labelMedium.copy(color = BurgundyAccent),
            )
        }

        AnimatedVisibility(visible = correctionExpanded) {
            CorrectionPanel(
                top3 = top3,
                correctionResults = correctionResults,
                currentCardName = lastScanCard?.name,
                onCorrect = onCorrect,
                onQueryChanged = onCorrectionQueryChanged,
            )
        }
    }
}
