package com.github.username.cardapp.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.LeatherDark
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import kotlinx.coroutines.delay

@Composable
internal fun CorrectionPanel(
    top3: List<ScanCandidate>,
    correctionResults: List<CardEntity>,
    currentCardName: String?,
    onCorrect: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(searchText.text) {
        if (searchText.text.length < 2) {
            onQueryChanged("")
        } else {
            delay(200)
            onQueryChanged(searchText.text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LeatherDeep.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Candidate chips
        val candidates = top3.filter { it.name != currentCardName }
        if (candidates.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (candidate in candidates) {
                    val chipInteraction = remember { MutableInteractionSource() }
                    val chipPressed by chipInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (chipPressed) GoldLight else GoldMuted,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (chipPressed) GoldDark.copy(alpha = 0.35f) else LeatherMid)
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null,
                            ) { onCorrect(candidate.name) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${candidate.name} (${candidate.score})",
                            style = Typography.labelSmall.copy(
                                color = if (chipPressed) CreamPrimary else CreamMuted,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Search field
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            textStyle = Typography.bodyMedium.copy(color = CreamPrimary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldDark, RoundedCornerShape(2.dp))
                        .background(LeatherDark, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (searchText.text.isEmpty()) {
                        Text(
                            text = "Search cards…",
                            style = Typography.bodyMedium.copy(color = GoldDark),
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Search results dropdown
        for (card in correctionResults) {
            val resultInteraction = remember { MutableInteractionSource() }
            val resultPressed by resultInteraction.collectIsPressedAsState()
            Text(
                text = card.name,
                style = Typography.bodyMedium.copy(
                    color = if (resultPressed) CreamPrimary else CreamMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (resultPressed) GoldDark.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(
                        interactionSource = resultInteraction,
                        indication = null,
                    ) { onCorrect(card.name) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
