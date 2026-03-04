package com.example.cardapp.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cardapp.data.local.CardEntity
import com.example.cardapp.ui.theme.CreamMuted
import com.example.cardapp.ui.theme.CreamPrimary
import com.example.cardapp.ui.theme.GoldMuted
import com.example.cardapp.ui.theme.GoldPrimary
import com.example.cardapp.ui.theme.LeatherDeep
import com.example.cardapp.ui.theme.LeatherLight
import com.example.cardapp.ui.theme.LeatherMid
import com.example.cardapp.ui.theme.Typography

@Composable
fun CardListScreen(vm: CollectionViewModel = viewModel()) {
    val cards by vm.cards.collectAsState()
    val syncState by vm.syncState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to LeatherLight,
                        0.5f to LeatherMid,
                        1.0f to LeatherDeep,
                    ),
                    radius = 1600f,
                )
            ),
    ) {
        when (val state = syncState) {
            is SyncState.Complete, is SyncState.Idle -> {
                if (cards.isEmpty()) {
                    SyncProgressContent(state)
                } else {
                    CardGrid(cards = cards)
                }
            }
            else -> SyncProgressContent(state)
        }
    }
}

@Composable
private fun CardGrid(cards: List<CardEntity>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        items(cards, key = { it.slug }) { card ->
            CardTile(card)
        }
    }
}

@Composable
private fun CardTile(card: CardEntity) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(4.dp))
                .background(LeatherMid),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/images/${card.slug}.webp")
                    .crossfade(true)
                    .build(),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = card.name,
            style = Typography.labelLarge.copy(color = CreamPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.setName,
            style = Typography.bodyLarge.copy(color = CreamMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncProgressContent(state: SyncState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val (label, showSpinner) = when (state) {
            is SyncState.SyncingCards -> "Loading cards…" to true
            is SyncState.Error -> "Error: ${state.message}" to false
            else -> "Preparing…" to true
        }

        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = GoldPrimary,
                trackColor = GoldMuted.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(24.dp))
        }

        Text(
            text = label,
            style = Typography.titleLarge.copy(
                color = if (state is SyncState.Error) GoldPrimary else CreamMuted,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
