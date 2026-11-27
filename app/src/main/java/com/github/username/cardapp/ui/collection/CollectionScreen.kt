package com.github.username.cardapp.ui.collection

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.CollectionImportResult
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.common.NoFilterResults
import com.github.username.cardapp.ui.common.PriceFormat
import com.github.username.cardapp.ui.common.SearchFilterBar
import com.github.username.cardapp.ui.common.SectionHeader
import com.github.username.cardapp.ui.common.countOf
import com.github.username.cardapp.ui.theme.BurgundyLight
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(onCardClick: (String) -> Unit = {}, vm: CollectionViewModel = hiltViewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val filterState by vm.filter.filterState.collectAsStateWithLifecycle()
    val totalCards by vm.totalCardCount.collectAsStateWithLifecycle()
    val setNames by vm.filter.setNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) vm.importCollection(uri)
    }

    val importOutcome by vm.importOutcome.collectAsStateWithLifecycle()
    importOutcome?.let { outcome ->
        ImportOutcomeDialog(outcome = outcome, onDismiss = vm::dismissImportOutcome)
    }

    CollectionScreenContent(
        entries = entries,
        filterState = filterState,
        totalCards = totalCards,
        availableSets = setNames,
        onUpdateFilter = vm.filter::updateFilter,
        onQueryChange = vm.filter::updateQuery,
        onClearFilters = vm.filter::clearFilters,
        onIncrement = { vm.increment(it) },
        onDecrement = { vm.decrement(it) },
        onCardClick = onCardClick,
        onImport = { importLauncher.launch("text/*") },
        onExport = {
            scope.launch {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.exportText())
                    putExtra(Intent.EXTRA_SUBJECT, "Sorcery Collection")
                }
                context.startActivity(Intent.createChooser(intent, "Export Collection"))
            }
        },
    )
}

@Composable
private fun CollectionScreenContent(
    entries: List<CollectionCardWithPrice>,
    filterState: CardFilterState = CardFilterState(),
    totalCards: Int = entries.sumOf { it.quantity },
    availableSets: List<String> = emptyList(),
    onUpdateFilter: ((CardFilterState) -> CardFilterState) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onCardClick: (String) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    var selectedCardName by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // Back to the top when the filter changes, but not when the screen re-enters
    // composition (returning from a card, rotating), which re-emits the current filter.
    val currentFilter by rememberUpdatedState(filterState)
    LaunchedEffect(listState) {
        snapshotFlow { currentFilter.copy(filtersExpanded = false) }
            .drop(1)
            .collect { listState.scrollToItem(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        ) {
            val totalValue = entries.sumOf { entry ->
                val price = entry.marketPrice ?: 0.0
                price * entry.quantity
            }
            CollectionHeader(
                totalCards = entries.sumOf { it.quantity },
                totalTotal = totalCards,
                totalValue = totalValue,
                hasFilter = filterState.hasActiveFilters,
                onExport = onExport,
                onImport = onImport,
            )
            SearchFilterBar(
                state = filterState,
                onUpdate = onUpdateFilter,
                onQueryChange = onQueryChange,
                availableSets = availableSets,
            )
            if (entries.isEmpty() && filterState.hasActiveFilters) {
                NoFilterResults(onClear = onClearFilters)
            } else if (entries.isEmpty()) {
                EmptyCollectionMessage()
            } else {
                // Clear the navigation bar, and the keyboard while searching.
                val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
                ) {
                    items(entries, key = { it.card.name }) { entry ->
                        CardRow(
                            card = entry.card,
                            count = entry.quantity,
                            isSelected = selectedCardName == entry.card.name,
                            onToggle = {
                                selectedCardName = if (selectedCardName == entry.card.name) null else entry.card.name
                            },
                            onLongPress = { onCardClick(entry.card.name) },
                            onIncrement = { onIncrement(entry.card.name) },
                            onDecrement = {
                                if (entry.quantity <= 1) selectedCardName = null
                                onDecrement(entry.card.name)
                            },
                            marketPrice = entry.marketPrice,
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
    totalCards: Int,
    totalTotal: Int,
    totalValue: Double,
    hasFilter: Boolean,
    modifier: Modifier = Modifier,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    SectionHeader(title = "COLLECTION", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(label = "IMPORT", contentDescription = "Import collection from a file", onClick = onImport)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (totalTotal > 0) {
                    val valueStr = PriceFormat.full(totalValue)
                    val subtitle = if (hasFilter) {
                        "${countOf(totalCards, "card")} · $valueStr (of $totalTotal)"
                    } else {
                        "${countOf(totalTotal, "card")} · $valueStr"
                    }
                    Text(
                        text = subtitle,
                        style = Typography.labelMedium.copy(color = CreamFaded, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            HeaderAction(
                label = "EXPORT",
                contentDescription = "Export collection",
                enabled = totalTotal > 0,
                onClick = onExport,
            )
        }
    }
}

@Composable
private fun HeaderAction(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .border(
                0.7.dp,
                if (enabled) GoldMuted.copy(alpha = 0.6f) else GoldDark.copy(alpha = 0.3f),
                RoundedCornerShape(2.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = Typography.labelMedium.copy(color = if (enabled) GoldPrimary else CreamFaded.copy(alpha = 0.4f)),
            modifier = Modifier.clearAndSetSemantics { },
        )
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
            text = "Scan cards or import a list to add them to your collection",
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun ImportOutcomeDialog(outcome: ImportOutcome, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LeatherMid, RoundedCornerShape(6.dp))
                .border(1.dp, GoldMuted, RoundedCornerShape(6.dp))
                .padding(20.dp),
        ) {
            Text(
                text = "IMPORT",
                style = Typography.titleMedium.copy(color = GoldPrimary),
            )
            Spacer(Modifier.height(12.dp))
            when (outcome) {
                is ImportOutcome.Imported -> ImportResultSummary(outcome.result)
                is ImportOutcome.Failed -> Text(
                    text = outcome.message,
                    style = Typography.bodyMedium.copy(color = BurgundyLight),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "CLOSE",
                style = Typography.labelMedium.copy(color = GoldPrimary),
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ImportResultSummary(result: CollectionImportResult) {
    Text(
        text = if (result.importedCount > 0) {
            "Added ${countOf(result.importedCount, "card")} (${result.entries.size} distinct) to your collection."
        } else {
            "No cards were imported."
        },
        style = Typography.bodyMedium.copy(color = CreamPrimary),
    )
    if (result.unmatched.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${countOf(result.unmatched.size, "line")} could not be matched to a card:",
            style = Typography.bodyMedium.copy(color = BurgundyLight),
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (line in result.unmatched) {
                Text(
                    text = line,
                    style = Typography.bodySmall.copy(color = CreamMuted),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionListPreview() {
    val fakeEntries = listOf(
        CollectionCardWithPrice(
            card = CardEntity(name = "Abundance", primarySlug = "abundance-alpha", elements = "Earth", subTypes = "", cardType = "Spell", rarity = "Elite", cost = 3, attack = 0, defence = 0, life = null, rulesText = "", airThreshold = 0, earthThreshold = 2, fireThreshold = 0, waterThreshold = 0),
            quantity = 2,
            marketPrice = 5.0,
            lowPrice = 4.0,
        ),
        CollectionCardWithPrice(
            card = CardEntity(name = "Ruby Core Werewolf", primarySlug = "ruby-core-werewolf-alpha", elements = "Fire", subTypes = "Beast", cardType = "Minion", rarity = "Ordinary", cost = 4, attack = 4, defence = 3, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 1, waterThreshold = 0),
            quantity = 1,
            marketPrice = 1.0,
            lowPrice = 0.8,
        ),
        CollectionCardWithPrice(
            card = CardEntity(name = "Volcanic Dragon", primarySlug = "volcanic-dragon-alpha", elements = "Fire", subTypes = "Dragon", cardType = "Minion", rarity = "Unique", cost = 8, attack = 7, defence = 7, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 3, waterThreshold = 0),
            quantity = 3,
            marketPrice = 20.0,
            lowPrice = 18.0,
        ),
    )
    CardAppTheme {
        CollectionScreenContent(entries = fakeEntries)
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
private fun ImportResultDialogPreview() {
    CardAppTheme {
        ImportOutcomeDialog(
            outcome = ImportOutcome.Imported(
                CollectionImportResult(
                    entries = listOf(
                        CollectionEntryEntity(cardName = "Abundance", quantity = 2),
                        CollectionEntryEntity(cardName = "Volcanic Dragon", quantity = 3),
                    ),
                    unmatched = listOf("2 Saphire Core", "Ruby Core"),
                ),
            ),
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportFailedDialogPreview() {
    CardAppTheme {
        ImportOutcomeDialog(
            outcome = ImportOutcome.Failed("The file could not be read."),
            onDismiss = {},
        )
    }
}
