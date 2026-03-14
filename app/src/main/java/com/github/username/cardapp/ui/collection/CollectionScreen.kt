package com.github.username.cardapp.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground

@Composable
fun CollectionScreen(vm: CollectionViewModel = viewModel()) {
    val entries by vm.entries.collectAsState()

    CollectionScreenContent(
        entries = entries,
        onIncrement = { vm.increment(it) },
        onDecrement = { vm.decrement(it) },
    )
}

@Composable
private fun CollectionScreenContent(
    entries: List<CollectionCardRow>,
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
) {
    var selectedCardName by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollectionHeader(
                uniqueCards = entries.size,
                totalCards = entries.sumOf { it.quantity },
                modifier = Modifier.statusBarsPadding(),
            )
            if (entries.isEmpty()) {
                EmptyCollectionMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(entries, key = { it.card.name }) { entry ->
                        CardRow(
                            card = entry.card,
                            count = entry.quantity,
                            isSelected = selectedCardName == entry.card.name,
                            onToggle = {
                                selectedCardName = if (selectedCardName == entry.card.name) null else entry.card.name
                            },
                            onIncrement = { onIncrement(entry.card.name) },
                            onDecrement = {
                                if (entry.quantity <= 1) selectedCardName = null
                                onDecrement(entry.card.name)
                            },
                        )
                        HorizontalDivider(color = GoldDark.copy(alpha = 0.25f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    uniqueCards: Int,
    totalCards: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
            Text(
                text = "  COLLECTION  ",
                style = Typography.labelLarge.copy(color = GoldPrimary),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
        }
        if (totalCards > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$totalCards cards \u00b7 $uniqueCards unique",
                style = Typography.labelMedium.copy(color = CreamFaded),
            )
        }
    }
}

@Composable
private fun EmptyCollectionMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NO CARDS COLLECTED",
            style = Typography.labelLarge.copy(color = CreamMuted),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Scan cards and add them to your collection",
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionEmptyPreview() {
    CardAppTheme {
        CollectionScreenContent(entries = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionListPreview() {
    val fakeEntries = listOf(
        CollectionCardRow(
            card = CardEntity(name = "Abundance", primarySlug = "abundance-alpha", elements = "Earth", subTypes = "", cardType = "Spell", rarity = "Elite", cost = 3, attack = 0, defence = 0, life = null, rulesText = "", airThreshold = 0, earthThreshold = 2, fireThreshold = 0, waterThreshold = 0),
            quantity = 2,
        ),
        CollectionCardRow(
            card = CardEntity(name = "Ruby Core Werewolf", primarySlug = "ruby-core-werewolf-alpha", elements = "Fire", subTypes = "Beast", cardType = "Minion", rarity = "Ordinary", cost = 4, attack = 4, defence = 3, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 1, waterThreshold = 0),
            quantity = 1,
        ),
        CollectionCardRow(
            card = CardEntity(name = "Volcanic Dragon", primarySlug = "volcanic-dragon-alpha", elements = "Fire", subTypes = "Dragon", cardType = "Minion", rarity = "Unique", cost = 8, attack = 7, defence = 7, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 3, waterThreshold = 0),
            quantity = 3,
        ),
    )
    CardAppTheme {
        CollectionScreenContent(entries = fakeEntries)
    }
}
