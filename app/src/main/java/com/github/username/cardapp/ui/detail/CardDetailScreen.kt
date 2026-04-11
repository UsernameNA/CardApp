package com.github.username.cardapp.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.username.cardapp.data.FaqEntry
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherLight
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.common.AlchemicalSymbol
import com.github.username.cardapp.ui.common.ElementThresholdGrid
import com.github.username.cardapp.ui.theme.leatherBackground
import com.github.username.cardapp.ui.theme.rarityColor

@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    vm: CardDetailViewModel = hiltViewModel(),
) {
    val card by vm.card.collectAsState()
    val variants by vm.variants.collectAsState()
    val prices by vm.prices.collectAsState()
    val faqs by vm.faqs.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .leatherBackground(),
    ) {
        if (card != null) {
            CardDetailContent(
                card = card!!,
                variants = variants,
                marketPrice = prices[card!!.name],
                faqs = faqs,
                onBack = onBack,
                onAddToCollection = vm::addToCollection,
            )
        }
    }
}

@Composable
private fun CardDetailContent(
    card: CardEntity,
    variants: List<CardVariantEntity>,
    marketPrice: Double?,
    faqs: List<FaqEntry> = emptyList(),
    onBack: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    var selectedSlug by remember(card.name) { mutableStateOf(card.primarySlug) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Back button
        Text(
            text = "\u2190  BACK",
            style = Typography.labelLarge.copy(color = GoldPrimary),
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(16.dp),
        )

        // Card image — uses selected variant slug
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/images/$selectedSlug.webp")
                    .crossfade(true)
                    .build(),
                contentDescription = card.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Card info section
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Name
            Text(
                text = card.name.uppercase(),
                style = Typography.titleLarge.copy(color = GoldPrimary),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            // Type line
            TypeLine(card)

            Spacer(Modifier.height(16.dp))
            GoldDivider()
            Spacer(Modifier.height(16.dp))

            // Stats row
            StatsRow(card)
            Spacer(Modifier.height(16.dp))
            GoldDivider()
            Spacer(Modifier.height(16.dp))

            // Rules text
            if (card.rulesText.isNotBlank()) {
                RulesText(
                    text = card.rulesText,
                )
                Spacer(Modifier.height(16.dp))
                GoldDivider()
                Spacer(Modifier.height(16.dp))
            }

            // Price
            if (marketPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "MARKET PRICE",
                        style = Typography.labelLarge.copy(color = CreamFaded),
                    )
                    Text(
                        text = "$%.2f".format(marketPrice),
                        style = Typography.labelLarge.copy(color = GoldPrimary),
                    )
                }
                Spacer(Modifier.height(16.dp))
                GoldDivider()
                Spacer(Modifier.height(16.dp))
            }

            // FAQs
            if (faqs.isNotEmpty()) {
                Text(
                    text = "FAQ",
                    style = Typography.labelLarge.copy(color = CreamFaded),
                )
                Spacer(Modifier.height(8.dp))
                faqs.forEach { faq ->
                    FaqRow(faq)
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            // Add to collection button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(0.8.dp, GoldMuted, RoundedCornerShape(2.dp))
                    .padding(2.dp)
                    .border(0.5.dp, GoldDark.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                    .background(LeatherMid.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                    .clickable(onClick = onAddToCollection),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ADD TO COLLECTION",
                    style = Typography.labelLarge.copy(color = CreamMuted),
                )
            }

            // Variants / printings (collapsible)
            if (variants.isNotEmpty()) {
                var printingsExpanded by remember { mutableStateOf(false) }
                Spacer(Modifier.height(16.dp))
                GoldDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (printingsExpanded) "PRINTINGS  \u25B2" else "PRINTINGS  \u25BC",
                    style = Typography.labelLarge.copy(color = CreamFaded),
                    modifier = Modifier.clickable { printingsExpanded = !printingsExpanded },
                )
                if (printingsExpanded) {
                    Spacer(Modifier.height(8.dp))
                    variants.forEach { variant ->
                        VariantRow(
                            variant = variant,
                            isSelected = variant.slug == selectedSlug,
                            onClick = { selectedSlug = variant.slug },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TypeLine(card: CardEntity) {
    val parts = buildList {
        add(card.cardType)
        if (card.subTypes.isNotBlank()) add(card.subTypes)
    }
    val rarity = card.rarity

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = parts.joinToString(" \u2014 "),
            style = Typography.titleMedium.copy(color = CreamMuted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = rarity.uppercase(),
            style = Typography.labelMedium.copy(color = rarityColor(rarity)),
        )
    }
}

@Composable
private fun StatsRow(card: CardEntity) {
    val thresholds = buildList {
        if (card.airThreshold > 0) add("Air" to card.airThreshold)
        if (card.earthThreshold > 0) add("Earth" to card.earthThreshold)
        if (card.fireThreshold > 0) add("Fire" to card.fireThreshold)
        if (card.waterThreshold > 0) add("Water" to card.waterThreshold)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val isSite = card.cardType.equals("Site", ignoreCase = true)
        val isAvatar = card.cardType.equals("Avatar", ignoreCase = true)
        if (!isAvatar && !isSite) {
            CostBlock(cost = card.cost, thresholds = thresholds)
        }
        if (isSite && thresholds.isNotEmpty()) {
            ElementThresholdGrid(
                thresholds = thresholds,
                singleSize = 22.sp,
                gridSize = 15.sp,
            )
        }
        val hasAtkDef = card.cardType.equals("Minion", ignoreCase = true) ||
            card.cardType.equals("Avatar", ignoreCase = true)
        if (hasAtkDef) {
            if (card.attack == card.defence) {
                StatBlock(label = "POWER", value = "${card.attack}")
            } else {
                StatBlock(label = "ATK", value = "${card.attack}")
                StatBlock(label = "DEF", value = "${card.defence}")
            }
        }
        if (card.life != null && !isSite) {
            StatBlock(label = "LIFE", value = "${card.life}")
        }
    }
}

@Composable
private fun CostBlock(cost: Int, thresholds: List<Pair<String, Int>>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$cost",
                style = Typography.titleLarge.copy(color = GoldPrimary, fontSize = 24.sp),
            )
            if (thresholds.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                ElementThresholdGrid(
                    thresholds = thresholds,
                    singleSize = 22.sp,
                    gridSize = 15.sp,
                )
            }
        }
        Text(
            text = "COST",
            style = Typography.labelMedium.copy(color = CreamFaded),
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = Typography.titleLarge.copy(color = GoldPrimary, fontSize = 24.sp),
        )
        Text(
            text = label,
            style = Typography.labelMedium.copy(color = CreamFaded),
        )
    }
}

@Composable
private fun VariantRow(
    variant: CardVariantEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) GoldPrimary else LeatherLight.copy(alpha = 0.4f)
    val bgColor = if (isSelected) GoldDark.copy(alpha = 0.25f) else LeatherLight.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, borderColor, RoundedCornerShape(4.dp))
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.setName,
                style = Typography.labelMedium.copy(
                    color = if (isSelected) GoldPrimary else CreamPrimary,
                ),
            )
            Text(
                text = variant.finish,
                style = Typography.bodyMedium.copy(color = CreamFaded),
            )
        }
        if (variant.artist.isNotBlank()) {
            Text(
                text = variant.artist,
                style = Typography.bodyMedium.copy(color = CreamFaded),
            )
        }
    }
}

@Composable
private fun FaqRow(faq: FaqEntry) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LeatherLight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = faq.question,
            style = Typography.bodyMedium.copy(color = CreamPrimary),
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = faq.answer,
                style = Typography.bodyMedium.copy(color = CreamMuted),
            )
        }
    }
}

@Composable
private fun GoldDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = GoldMuted.copy(alpha = 0.3f),
    )
}

private val circledDigits = (1..20).associate { "($it)" to (0x2460 + it - 1).toChar().toString() }

private val elementPlaceholders = mapOf(
    "(A)" to "air",
    "(E)" to "earth",
    "(F)" to "fire",
    "(W)" to "water",
)

@Composable
private fun RulesText(text: String) {
    val style = Typography.bodyLarge.copy(color = CreamPrimary)
    val fontSize = style.fontSize

    // Strip "UPDATED: " prefix from errata'd cards, append asterisk
    val isErrata = text.startsWith("UPDATED: ")
    val cleaned = if (isErrata) text.removePrefix("UPDATED: ") + " *" else text
    // Normalize (1) (2) etc. to circled Unicode digits ① ②
    val normalized = circledDigits.entries.fold(cleaned) { acc, (token, circled) -> acc.replace(token, circled) }

    // Build annotated string replacing element tokens with inline content IDs
    val annotated = buildAnnotatedString {
        var remaining = normalized
        while (remaining.isNotEmpty()) {
            val nextMatch = elementPlaceholders.keys
                .mapNotNull { token -> remaining.indexOf(token).takeIf { it >= 0 }?.let { token to it } }
                .minByOrNull { it.second }

            if (nextMatch == null) {
                append(remaining)
                break
            }

            val (token, index) = nextMatch
            append(remaining.substring(0, index))
            appendInlineContent(elementPlaceholders[token]!!, token)
            remaining = remaining.substring(index + token.length)
        }
    }

    val inlineContent = elementPlaceholders.values.associateWith { element ->
        InlineTextContent(
            placeholder = Placeholder(fontSize, fontSize * 1.4, PlaceholderVerticalAlign.Center),
        ) {
            AlchemicalSymbol(element = element, fontSize = fontSize)
        }
    }

    Text(
        text = annotated,
        style = style,
        inlineContent = inlineContent,
    )
}

@Preview(showBackground = true)
@Composable
private fun CardDetailPreview() {
    val card = CardEntity(
        name = "Apprentice Wizard",
        primarySlug = "alp-apprentice_wizard-b-s",
        elements = "Air",
        subTypes = "Mortal",
        cardType = "Minion",
        rarity = "Ordinary",
        cost = 3,
        attack = 1,
        defence = 1,
        life = null,
        rulesText = "Spellcaster\nGenesis \u2192 Draw a spell.",
        airThreshold = 1,
        earthThreshold = 0,
        fireThreshold = 0,
        waterThreshold = 0,
    )
    val variants = listOf(
        CardVariantEntity(slug = "alp-apprentice_wizard-b-s", cardName = "Apprentice Wizard", setName = "Alpha", finish = "Standard", product = "Booster", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
        CardVariantEntity(slug = "alp-apprentice_wizard-b-f", cardName = "Apprentice Wizard", setName = "Alpha", finish = "Foil", product = "Booster", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
        CardVariantEntity(slug = "bet-apprentice_wizard-b-s", cardName = "Apprentice Wizard", setName = "Beta", finish = "Standard", product = "Booster", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
        CardVariantEntity(slug = "bet-apprentice_wizard-b-f", cardName = "Apprentice Wizard", setName = "Beta", finish = "Foil", product = "Booster", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
        CardVariantEntity(slug = "pro-apprentice_wizard-wk-s", cardName = "Apprentice Wizard", setName = "Arthurian Legends Promo", finish = "Standard", product = "Welcome Kit", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
        CardVariantEntity(slug = "pro-apprentice_wizard-wk-f", cardName = "Apprentice Wizard", setName = "Arthurian Legends Promo", finish = "Foil", product = "Welcome Kit", artist = "Ossi Hiekkala", flavorText = "", typeText = "An Ordinary Mortal new to power"),
    )
    CardAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .leatherBackground(),
        ) {
            CardDetailContent(
                card = card,
                variants = variants,
                marketPrice = 0.05,
                onBack = {},
                onAddToCollection = {},
            )
        }
    }
}
