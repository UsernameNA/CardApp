package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.data.model.CardJson

/** Turns the bundled `cards.json` into database rows. */
object CatalogueBuilder {

    private val primarySlugOverrides = mapOf(
        "Apprentice Wizard" to "999-apprentice_wizard-wk-s",
        "Grandmaster Wizard" to "999-grandmaster_wizard-wk-s",
    )

    /**
     * @param imageSlugs the bundled card images; a card's display image is the first
     *   print that actually has one, so a card is only blank when none of its prints do.
     */
    fun build(cardList: List<CardJson>, imageSlugs: Set<String>): Catalogue {
        val setReleases = linkedMapOf<String, String>()
        for (card in cardList) {
            for (set in card.sets) {
                val name = set.name ?: continue
                if (name !in setReleases) setReleases[name] = set.releasedAt.orEmpty()
            }
        }
        val setEntities = setReleases.entries
            .sortedBy { it.value }
            .mapIndexed { index, (name, releasedAt) ->
                SetEntity(name = name, releasedAt = releasedAt, releaseOrder = index + 1)
            }
        val setOrder = setEntities.associate { it.name to it.releaseOrder }

        val cardEntities = cardList.map { card ->
            val g = card.guardian
            val setNames = card.sets.mapNotNullTo(linkedSetOf()) { it.name }
            CardEntity(
                name = card.name,
                primarySlug = primarySlug(card, setOrder, imageSlugs),
                elements = card.elements.orEmpty(),
                subTypes = card.subTypes.orEmpty(),
                cardType = g?.type.orEmpty(),
                rarity = g?.rarity.orEmpty(),
                cost = g?.cost ?: 0,
                attack = g?.attack ?: 0,
                defence = g?.defence ?: 0,
                life = g?.life,
                rulesText = g?.rulesText.orEmpty(),
                airThreshold = g?.thresholds?.air ?: 0,
                earthThreshold = g?.thresholds?.earth ?: 0,
                fireThreshold = g?.thresholds?.fire ?: 0,
                waterThreshold = g?.thresholds?.water ?: 0,
                setNames = if (setNames.isEmpty()) "" else setNames.joinToString(",", prefix = ",", postfix = ","),
            )
        }

        // Variants reference their set by name, so a set without one has nothing to join to.
        val variantEntities = cardList.flatMap { card ->
            card.sets.filter { it.name != null }.flatMap { set ->
                set.variants.map { variant ->
                    CardVariantEntity(
                        slug = variant.slug,
                        cardName = card.name,
                        setName = set.name.orEmpty(),
                        finish = variant.finish.orEmpty(),
                        product = variant.product.orEmpty(),
                        artist = variant.artist.orEmpty(),
                        flavorText = variant.flavorText.orEmpty(),
                        typeText = variant.typeText.orEmpty(),
                    )
                }
            }
        }

        return Catalogue(setEntities, cardEntities, variantEntities)
    }

    /**
     * Preference order: earliest set first, standard finish before others within a set.
     * The first candidate with a bundled image wins; with none, the first candidate.
     */
    private fun primarySlug(card: CardJson, setOrder: Map<String, Int>, imageSlugs: Set<String>): String {
        primarySlugOverrides[card.name]?.let { return it }
        val candidates = card.sets
            .sortedBy { setOrder[it.name] ?: Int.MAX_VALUE }
            .flatMap { set ->
                val (standard, other) = set.variants.partition { it.finish.equals("Standard", ignoreCase = true) }
                (standard + other).map { it.slug }
            }
        return candidates.firstOrNull { it in imageSlugs } ?: candidates.firstOrNull().orEmpty()
    }
}
