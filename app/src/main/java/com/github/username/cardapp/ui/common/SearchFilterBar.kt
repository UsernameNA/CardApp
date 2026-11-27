package com.github.username.cardapp.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.SortDir
import com.github.username.cardapp.data.SortField
import com.github.username.cardapp.data.toggle
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherLight
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.elementColor

private val CARD_TYPES = listOf("Minion", "Site", "Aura", "Avatar", "Artifact", "Magic")
private val RARITIES = listOf("Ordinary", "Exceptional", "Elite", "Unique")
private val ELEMENTS = listOf("Fire", "Water", "Earth", "Air", "None")

/**
 * Search field, sort toggles and filter panel for a card list. The field owns the text
 * as it is typed and reports it through [onQueryChange]; every other change reaches
 * [onUpdate] as a transform of the latest state.
 */
@Composable
fun SearchFilterBar(
    state: CardFilterState,
    onUpdate: ((CardFilterState) -> CardFilterState) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    availableSets: List<String> = emptyList(),
) {
    val focusManager = LocalFocusManager.current

    // Seeded from the view model rather than saved, so after navigating back or a
    // configuration change the field always shows the query the list is filtered by.
    val queryState = remember { TextFieldState(state.query) }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }.collect { currentOnQueryChange(it) }
    }
    // "Clear filters" elsewhere on the screen resets the query; mirror it in the field.
    LaunchedEffect(state.query) {
        if (state.query.isEmpty() && queryState.text.isNotEmpty()) queryState.clearText()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Search row: text field + filter toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .border(
                        width = 0.7.dp,
                        color = if (queryState.text.isNotBlank()) GoldPrimary.copy(alpha = 0.7f) else GoldDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .background(LeatherMid.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(start = 10.dp, end = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (queryState.text.isEmpty()) {
                    Text(
                        text = "SEARCH...",
                        style = Typography.labelMedium.copy(
                            color = CreamFaded.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        state = queryState,
                        textStyle = Typography.labelMedium.copy(color = CreamPrimary),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        cursorBrush = SolidColor(GoldPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onKeyboardAction = { focusManager.clearFocus() },
                        modifier = Modifier.weight(1f),
                    )
                    if (queryState.text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(28.dp)
                                .clickable(role = Role.Button) {
                                    queryState.clearText()
                                    focusManager.clearFocus()
                                }
                                .semantics { contentDescription = "Clear search" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "×",
                                style = Typography.labelLarge.copy(color = CreamFaded),
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Filter toggle button
            val filterActive = state.sets.isNotEmpty() || state.types.isNotEmpty() || state.rarities.isNotEmpty() || state.elements.isNotEmpty()
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .border(
                        width = 0.7.dp,
                        color = if (filterActive) GoldPrimary else GoldDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .background(
                        if (state.filtersExpanded) LeatherLight.copy(alpha = 0.8f) else Color.Transparent,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable(role = Role.Button) { onUpdate { it.copy(filtersExpanded = !it.filtersExpanded) } }
                    .semantics {
                        contentDescription = if (filterActive) "Filters, some applied" else "Filters"
                        stateDescription = if (state.filtersExpanded) "Expanded" else "Collapsed"
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filterActive) "◆ FILTER" else "FILTER",
                    style = Typography.labelMedium.copy(
                        color = if (filterActive) GoldPrimary else CreamFaded,
                    ),
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

        }

        // Sort toggles row; the toggles' touch-target margin spaces it from the search row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((label, field) in SORT_TOGGLES) {
                SortToggle(label, state.sort.dir(field)) {
                    onUpdate { it.copy(sort = it.sort.toggle(field)) }
                }
            }
        }

        // Active filter summary chips (when collapsed but filters active)
        if (!state.filtersExpanded && state.hasActiveFilters) {
            ActiveFilterSummary(
                state = state,
                onUpdate = onUpdate,
                onClearQuery = { queryState.clearText() },
            )
        }

        // Expandable filter panel
        AnimatedVisibility(
            visible = state.filtersExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FilterPanel(state = state, onUpdate = onUpdate, availableSets = availableSets)
        }

        HorizontalDivider(thickness = 0.5.dp, color = GoldDark.copy(alpha = 0.25f))
    }
}

private val SORT_TOGGLES = listOf(
    "NAME" to SortField.Name,
    "COST" to SortField.Cost,
    "RARITY" to SortField.Rarity,
    "PRICE" to SortField.Price,
)

@Composable
private fun ActiveFilterSummary(
    state: CardFilterState,
    onUpdate: ((CardFilterState) -> CardFilterState) -> Unit,
    onClearQuery: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (s in state.sets) {
            DismissChip(label = s, contentDescription = "Remove $s filter") {
                onUpdate { it.copy(sets = it.sets - s) }
            }
        }
        for (t in state.types) {
            DismissChip(label = t, contentDescription = "Remove $t filter") {
                onUpdate { it.copy(types = it.types - t) }
            }
        }
        for (r in state.rarities) {
            DismissChip(label = r, contentDescription = "Remove $r filter") {
                onUpdate { it.copy(rarities = it.rarities - r) }
            }
        }
        for (e in state.elements) {
            DismissChip(label = e, contentDescription = "Remove $e filter") {
                onUpdate { it.copy(elements = it.elements - e) }
            }
        }
        if (state.hasActiveFilters) {
            DismissChip(label = "CLEAR ALL", contentDescription = "Clear search and all filters") {
                onClearQuery()
                onUpdate { it.copy(sets = emptySet(), types = emptySet(), rarities = emptySet(), elements = emptySet(), query = "") }
            }
        }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun DismissChip(label: String, contentDescription: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .border(0.7.dp, GoldMuted.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .background(GoldDark.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "× $label",
            style = Typography.labelMedium.copy(color = GoldPrimary),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    state: CardFilterState,
    onUpdate: ((CardFilterState) -> CardFilterState) -> Unit,
    availableSets: List<String>,
) {
    // Chips reserve 48dp touch targets, which also space their rows apart.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // subtle top border
            .drawWithContent {
                drawContent()
                drawLine(
                    GoldDark.copy(alpha = 0.3f),
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    0.5.dp.toPx(),
                )
            }
            .padding(top = 8.dp),
    ) {
        // Set row
        if (availableSets.isNotEmpty()) {
            FilterSectionLabel("SET")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (setName in availableSets) {
                    FilterChip(
                        label = setName.uppercase(),
                        selected = setName in state.sets,
                        onClick = { onUpdate { it.copy(sets = it.sets.toggled(setName)) } },
                    )
                }
            }
        }

        // Type row
        FilterSectionLabel("TYPE")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (type in CARD_TYPES) {
                FilterChip(
                    label = type.uppercase(),
                    selected = type in state.types,
                    onClick = { onUpdate { it.copy(types = it.types.toggled(type)) } },
                )
            }
        }

        // Rarity row
        FilterSectionLabel("RARITY")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rarity in RARITIES) {
                FilterChip(
                    label = rarity.uppercase(),
                    selected = rarity in state.rarities,
                    onClick = { onUpdate { it.copy(rarities = it.rarities.toggled(rarity)) } },
                )
            }
        }

        // Element row with AND/OR toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterSectionLabel("ELEMENT")
            Spacer(Modifier.weight(1f))
            if (state.elements.count { it != "None" } >= 2) {
                // No 48dp minimum here: it would push the element chips down as this appears.
                Box(
                    modifier = Modifier
                        .border(
                            0.7.dp,
                            if (state.elementMatchAll) GoldPrimary else GoldDark.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp),
                        )
                        .background(
                            if (state.elementMatchAll) GoldDark.copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(2.dp),
                        )
                        .clickable(role = Role.Button) { onUpdate { it.copy(elementMatchAll = !it.elementMatchAll) } }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (state.elementMatchAll) "MATCH ALL" else "MATCH ANY",
                        style = Typography.labelMedium.copy(
                            color = if (state.elementMatchAll) GoldPrimary else CreamFaded,
                        ),
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (element in ELEMENTS) {
                FilterChip(
                    label = element.uppercase(),
                    selected = element in state.elements,
                    onClick = { onUpdate { it.copy(elements = it.elements.toggled(element)) } },
                    accentColor = if (element != "None") elementColor(element) else null,
                )
            }
        }
    }
}

@Composable
fun NoFilterResults(
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NO MATCHES",
            style = Typography.labelLarge.copy(color = CreamMuted),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .border(0.7.dp, GoldMuted, RoundedCornerShape(2.dp))
                .clickable(role = Role.Button, onClick = onClear)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = "CLEAR FILTERS",
                style = Typography.labelMedium.copy(color = GoldPrimary),
            )
        }
    }
}

@Composable
private fun SortToggle(label: String, dir: SortDir, onClick: () -> Unit) {
    val arrow = when (dir) {
        SortDir.Off -> ""
        SortDir.Asc -> " ▲"
        SortDir.Desc -> " ▼"
    }
    val active = dir != SortDir.Off
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(26.dp)
            .border(
                0.7.dp,
                if (active) GoldPrimary.copy(alpha = 0.7f) else GoldDark.copy(alpha = 0.35f),
                RoundedCornerShape(2.dp),
            )
            .background(
                if (active) GoldDark.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(2.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Sort by ${label.lowercase()}"
                stateDescription = when (dir) {
                    SortDir.Off -> "Off"
                    SortDir.Asc -> "Ascending"
                    SortDir.Desc -> "Descending"
                }
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label$arrow",
            style = Typography.labelMedium.copy(
                color = if (active) GoldPrimary else CreamFaded.copy(alpha = 0.6f),
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = Typography.labelMedium.copy(
            color = GoldMuted.copy(alpha = 0.7f),
        ),
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null,
) {
    val borderColor = when {
        selected && accentColor != null -> accentColor.copy(alpha = 0.8f)
        selected -> GoldPrimary
        else -> GoldDark.copy(alpha = 0.4f)
    }
    val bgColor = when {
        selected && accentColor != null -> accentColor.copy(alpha = 0.15f)
        selected -> GoldDark.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    val textColor = when {
        selected && accentColor != null -> accentColor
        selected -> GoldPrimary
        else -> CreamFaded
    }

    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .border(0.7.dp, borderColor, RoundedCornerShape(2.dp))
            .background(bgColor, RoundedCornerShape(2.dp))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected && accentColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accentColor, RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = Typography.labelMedium.copy(color = textColor),
        )
    }
}
