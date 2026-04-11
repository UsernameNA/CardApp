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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary

import com.github.username.cardapp.ui.theme.LeatherLight
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography

import com.github.username.cardapp.ui.theme.CreamMuted

private val CARD_TYPES = listOf("Minion", "Site", "Aura", "Avatar", "Artifact", "Magic")
private val RARITIES = listOf("Ordinary", "Exceptional", "Elite", "Unique")
private val ELEMENTS = listOf("Fire", "Water", "Earth", "Air", "None")

@Composable
fun SearchFilterBar(
    state: CardFilterState,
    onUpdate: (CardFilterState) -> Unit,
    availableSets: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

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
                        color = if (state.query.isNotBlank()) GoldPrimary.copy(alpha = 0.7f) else GoldDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .background(LeatherMid.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(start = 10.dp, end = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.query.isEmpty()) {
                    Text(
                        text = "SEARCH...",
                        style = Typography.labelMedium.copy(
                            color = CreamFaded.copy(alpha = 0.5f),
                        ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = { onUpdate(state.copy(query = it)) },
                        textStyle = Typography.labelMedium.copy(color = CreamPrimary),
                        singleLine = true,
                        cursorBrush = SolidColor(GoldPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    if (state.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    onUpdate(state.copy(query = ""))
                                    focusManager.clearFocus()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "\u00d7",
                                style = Typography.labelLarge.copy(color = CreamFaded),
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
                    .clickable { onUpdate(state.copy(filtersExpanded = !state.filtersExpanded)) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filterActive) "\u25C6 FILTER" else "FILTER",
                    style = Typography.labelMedium.copy(
                        color = if (filterActive) GoldPrimary else CreamFaded,
                    ),
                )
            }

        }

        // Sort toggles row
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SortToggle("NAME", state.sort.name) {
                onUpdate(state.copy(sort = state.sort.toggle("name")))
            }
            SortToggle("COST", state.sort.cost) {
                onUpdate(state.copy(sort = state.sort.toggle("cost")))
            }
            SortToggle("RARITY", state.sort.rarity) {
                onUpdate(state.copy(sort = state.sort.toggle("rarity")))
            }
            SortToggle("PRICE", state.sort.price) {
                onUpdate(state.copy(sort = state.sort.toggle("price")))
            }
        }

        // Active filter summary chips (when collapsed but filters active)
        if (!state.filtersExpanded && state.hasActiveFilters) {
            Spacer(Modifier.height(6.dp))
            ActiveFilterSummary(state = state, onUpdate = onUpdate)
        }

        // Expandable filter panel
        AnimatedVisibility(
            visible = state.filtersExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FilterPanel(state = state, onUpdate = onUpdate, availableSets = availableSets)
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.5.dp, color = GoldDark.copy(alpha = 0.25f))
    }
}

@Composable
private fun ActiveFilterSummary(
    state: CardFilterState,
    onUpdate: (CardFilterState) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (s in state.sets) {
            DismissChip(label = s) {
                onUpdate(state.copy(sets = state.sets - s))
            }
        }
        for (t in state.types) {
            DismissChip(label = t) {
                onUpdate(state.copy(types = state.types - t))
            }
        }
        for (r in state.rarities) {
            DismissChip(label = r) {
                onUpdate(state.copy(rarities = state.rarities - r))
            }
        }
        for (e in state.elements) {
            DismissChip(label = e) {
                onUpdate(state.copy(elements = state.elements - e))
            }
        }
        if (state.hasActiveFilters) {
            DismissChip(label = "CLEAR ALL") {
                onUpdate(state.copy(sets = emptySet(), types = emptySet(), rarities = emptySet(), elements = emptySet(), query = ""))
            }
        }
    }
}

@Composable
private fun DismissChip(label: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .border(0.7.dp, GoldMuted.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .background(GoldDark.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "\u00d7 $label",
            style = Typography.labelMedium.copy(color = GoldPrimary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    state: CardFilterState,
    onUpdate: (CardFilterState) -> Unit,
    availableSets: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
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
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (setName in availableSets) {
                    FilterChip(
                        label = setName.uppercase(),
                        selected = setName in state.sets,
                        onClick = {
                            val newSets = if (setName in state.sets) state.sets - setName else state.sets + setName
                            onUpdate(state.copy(sets = newSets))
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Type row
        FilterSectionLabel("TYPE")
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (type in CARD_TYPES) {
                FilterChip(
                    label = type.uppercase(),
                    selected = type in state.types,
                    onClick = {
                        val newTypes = if (type in state.types) state.types - type else state.types + type
                        onUpdate(state.copy(types = newTypes))
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Rarity row
        FilterSectionLabel("RARITY")
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (rarity in RARITIES) {
                FilterChip(
                    label = rarity.uppercase(),
                    selected = rarity in state.rarities,
                    onClick = {
                        val newRarities = if (rarity in state.rarities) state.rarities - rarity else state.rarities + rarity
                        onUpdate(state.copy(rarities = newRarities))
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Element row with AND/OR toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterSectionLabel("ELEMENT")
            Spacer(Modifier.weight(1f))
            if (state.elements.count { it != "None" } >= 2) {
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
                        .clickable { onUpdate(state.copy(elementMatchAll = !state.elementMatchAll)) }
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
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (element in ELEMENTS) {
                FilterChip(
                    label = element.uppercase(),
                    selected = element in state.elements,
                    onClick = {
                        val newElements = if (element in state.elements) state.elements - element else state.elements + element
                        onUpdate(state.copy(elements = newElements))
                    },
                    accentColor = if (element != "None") com.github.username.cardapp.ui.theme.elementColor(element) else null,
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
                .clickable(onClick = onClear)
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
        SortDir.Asc -> " \u25B2"
        SortDir.Desc -> " \u25BC"
    }
    val active = dir != SortDir.Off
    Box(
        modifier = Modifier
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
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label$arrow",
            style = Typography.labelMedium.copy(
                color = if (active) GoldPrimary else CreamFaded.copy(alpha = 0.6f),
            ),
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
            .border(0.7.dp, borderColor, RoundedCornerShape(2.dp))
            .background(bgColor, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
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
