package com.github.username.cardapp.ui.cards

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.github.username.cardapp.ui.theme.CardAppTheme
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.NoFilterResults
import com.github.username.cardapp.ui.common.SearchFilterBar
import com.github.username.cardapp.ui.theme.BurgundyLight
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.leatherBackground
import com.github.username.cardapp.ui.theme.rarityColor

@Composable
fun CardsScreen(vm: CardsViewModel = hiltViewModel()) {
    val cards by vm.cards.collectAsState()
    val totalCount by vm.totalCardCount.collectAsState()
    val syncState by vm.syncState.collectAsState()
    val filterState by vm.filterState.collectAsState()

    val gridState = rememberLazyGridState()
    // Scroll key: everything except filtersExpanded
    val scrollKey = remember(filterState) {
        filterState.copy(filtersExpanded = false)
    }
    LaunchedEffect(scrollKey) {
        gridState.scrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CardsHeader(
                cardCount = cards.size,
                totalCount = totalCount,
                hasFilter = filterState.hasActiveFilters,
                modifier = Modifier.statusBarsPadding(),
            )
            SearchFilterBar(
                state = filterState,
                onUpdate = { newState -> vm.updateFilter { newState } },
            )
            when (val state = syncState) {
                is SyncState.Complete, is SyncState.Idle -> {
                    if (cards.isEmpty() && filterState.hasActiveFilters) {
                        NoFilterResults(onClear = { vm.updateFilter { CardFilterState() } })
                    } else if (cards.isEmpty()) {
                        CatalogueLoadingState(state = state)
                    } else {
                        CardGrid(cards = cards, gridState = gridState)
                    }
                }
                else -> CatalogueLoadingState(state = state)
            }
        }
    }
}

@Composable
private fun CardsHeader(
    cardCount: Int,
    totalCount: Int,
    hasFilter: Boolean,
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
                text = "  CARDS  ",
                style = Typography.labelLarge.copy(color = GoldPrimary),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
        }
        if (totalCount > 0) {
            Spacer(Modifier.height(4.dp))
            val subtitle = if (hasFilter) {
                "$cardCount of $totalCount entries catalogued"
            } else {
                "$totalCount entries catalogued"
            }
            Text(
                text = subtitle,
                style = Typography.labelMedium.copy(color = CreamFaded),
            )
        }
    }
}

@Composable
private fun CardGrid(
    cards: List<CardEntity>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = 4.dp,
            bottom = 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(cards, key = { it.name }) { card ->
            CardTile(card)
        }
    }
}

@Composable
private fun CardTile(card: CardEntity) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(3.dp))
                .background(LeatherMid),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/images/${card.primarySlug}.webp")
                    .crossfade(true)
                    .build(),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Rarity color stripe at the bottom of the card image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(rarityColor(card.rarity).copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = card.name.uppercase(),
            style = Typography.labelMedium.copy(
                color = CreamPrimary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.cardType,
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CatalogueLoadingState(state: SyncState) {
    val infiniteTransition = rememberInfiniteTransition(label = "catalogue")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state !is SyncState.Error) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val s = size.minDimension / 2f
                val strokeW = 1.5.dp.toPx()
                val alpha = 0.3f + breathe * 0.7f
                val scale = 0.75f + breathe * 0.25f

                // Outer pulsing diamond
                val outerPath = Path().apply {
                    moveTo(center.x, center.y - s * scale)
                    lineTo(center.x + s * 0.65f * scale, center.y)
                    lineTo(center.x, center.y + s * scale)
                    lineTo(center.x - s * 0.65f * scale, center.y)
                    close()
                }
                drawPath(
                    outerPath,
                    color = GoldPrimary.copy(alpha = alpha),
                    style = Stroke(width = strokeW),
                )

                // Inner fill (subtle)
                val innerPath = Path().apply {
                    val innerS = s * 0.45f * scale
                    moveTo(center.x, center.y - innerS)
                    lineTo(center.x + innerS * 0.65f, center.y)
                    lineTo(center.x, center.y + innerS)
                    lineTo(center.x - innerS * 0.65f, center.y)
                    close()
                }
                drawPath(
                    innerPath,
                    color = GoldMuted.copy(alpha = alpha * 0.4f),
                )

                // Four corner accent dots
                val dotR = 2.5f
                val dotOffset = s * 0.95f * scale
                drawCircle(GoldLight.copy(alpha = alpha * 0.9f), dotR, Offset(center.x, center.y - dotOffset))
                drawCircle(GoldLight.copy(alpha = alpha * 0.9f), dotR, Offset(center.x + dotOffset * 0.65f, center.y))
                drawCircle(GoldLight.copy(alpha = alpha * 0.9f), dotR, Offset(center.x, center.y + dotOffset))
                drawCircle(GoldLight.copy(alpha = alpha * 0.9f), dotR, Offset(center.x - dotOffset * 0.65f, center.y))
            }
            Spacer(Modifier.height(28.dp))
        }

        val (label, isError) = when (state) {
            is SyncState.SyncingCards -> "CATALOGUING" to false
            is SyncState.Error -> "ERROR" to true
            else -> "PREPARING" to false
        }

        Text(
            text = label,
            style = Typography.labelLarge.copy(
                color = if (isError) BurgundyLight else CreamMuted.copy(alpha = 0.5f + breathe * 0.5f),
                textAlign = TextAlign.Center,
            ),
        )
        if (isError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = (state as SyncState.Error).message,
                style = Typography.bodyMedium.copy(
                    color = CreamFaded,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun CardListGridPreview() {
    val fakeCards = listOf(
        CardEntity(name = "Ember Drake", primarySlug = "alp-ember_drake-b-s", elements = "Fire", subTypes = "", cardType = "Minion", rarity = "Exceptional", cost = 3, attack = 2, defence = 1, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 2, waterThreshold = 0),
        CardEntity(name = "Tide Caller", primarySlug = "alp-tide_caller-b-s", elements = "Water", subTypes = "", cardType = "Spell", rarity = "Ordinary", cost = 2, attack = 0, defence = 0, life = null, rulesText = "", airThreshold = 0, earthThreshold = 0, fireThreshold = 0, waterThreshold = 2),
        CardEntity(name = "Iron Sentinel", primarySlug = "alp-iron_sentinel-b-s", elements = "Earth", subTypes = "Guardian", cardType = "Minion", rarity = "Unique", cost = 5, attack = 4, defence = 4, life = null, rulesText = "", airThreshold = 0, earthThreshold = 3, fireThreshold = 0, waterThreshold = 0),
    )
    CardAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .leatherBackground(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CardsHeader(
                    cardCount = fakeCards.size,
                    totalCount = fakeCards.size,
                    hasFilter = false,
                    modifier = Modifier.statusBarsPadding(),
                )
                CardGrid(cards = fakeCards)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CardListLoadingPreview() {
    CardAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .leatherBackground(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CardsHeader(cardCount = 0, totalCount = 0, hasFilter = false, modifier = Modifier.statusBarsPadding())
                CatalogueLoadingState(state = SyncState.SyncingCards)
            }
        }
    }
}
