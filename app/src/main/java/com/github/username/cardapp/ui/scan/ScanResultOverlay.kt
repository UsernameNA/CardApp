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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.common.CardArt
import androidx.compose.ui.layout.ContentScale
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import kotlinx.coroutines.delay

@Composable
internal fun ScanResultOverlay(
    outcome: ScanOutcome?,
    correctionExpanded: Boolean,
    correctionResults: List<CardEntity>,
    onCorrect: (outcomeId: Long, cardName: String) -> Unit,
    onCorrectionOpenChanged: (Boolean) -> Unit,
    onCorrectionQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayAlpha = remember { Animatable(0f) }

    // Each new scan pops in and fades after a delay; so does closing the correction panel.
    // Keyed on the id, not the card, so scanning the same card (or missing) twice in a row
    // still shows the second result.
    LaunchedEffect(outcome?.id, correctionExpanded) {
        if (outcome == null) {
            overlayAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        overlayAlpha.snapTo(1f)
        if (correctionExpanded) return@LaunchedEffect
        delay(3000)
        overlayAlpha.animateTo(0f, tween(500))
    }

    // Leave the composition whenever fully faded — an invisible overlay must not
    // keep intercepting taps (its clickable would silently toggle the correction panel).
    val visible by remember { derivedStateOf { overlayAlpha.value > 0f } }
    if (!visible || outcome == null) return

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
            ) { onCorrectionOpenChanged(!correctionExpanded) }
            .padding(10.dp),
    ) {
        val card = outcome.card
        if (card != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CardArt(
                    slug = card.primarySlug,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    crossfade = false,
                    modifier = Modifier
                        .size(width = 48.dp, height = 67.dp)
                        .border(0.5.dp, GoldDark, RoundedCornerShape(2.dp)),
                )
                Column {
                    Text(
                        text = card.name.uppercase(),
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
                top3 = outcome.top3,
                correctionResults = correctionResults,
                currentCardName = card?.name,
                onCorrect = { onCorrect(outcome.id, it) },
                onQueryChanged = onCorrectionQueryChanged,
            )
        }
    }
}
