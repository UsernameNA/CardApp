package com.github.username.cardapp

import com.github.username.cardapp.data.CatalogueBuilder
import com.github.username.cardapp.data.model.CardJson
import com.github.username.cardapp.data.model.CardSetJson
import com.github.username.cardapp.data.model.CardVariantJson
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueBuilderTest {

    private val alpha = "2023-01-01"
    private val beta = "2023-06-01"

    private fun set(name: String?, releasedAt: String, vararg variants: Pair<String, String>) =
        CardSetJson(name = name, releasedAt = releasedAt, variants = variants.map { (slug, finish) -> CardVariantJson(slug, finish) })

    private fun primarySlug(card: CardJson, images: Set<String>) =
        CatalogueBuilder.build(listOf(card), images).cards.single().primarySlug

    private val harbinger = CardJson(
        name = "Harbinger",
        sets = listOf(
            set("Beta", beta, "002-harbinger-b-s" to "Standard"),
            set("Alpha", alpha, "001-harbinger-a-f" to "Foil", "001-harbinger-a-s" to "Standard"),
        ),
    )

    @Test
    fun earliestStandardPrintWinsWhenItHasArt() {
        val images = setOf("001-harbinger-a-s", "002-harbinger-b-s")
        assertEquals("001-harbinger-a-s", primarySlug(harbinger, images))
    }

    @Test
    fun printWithoutArtIsSkippedForOneThatHasIt() {
        assertEquals("002-harbinger-b-s", primarySlug(harbinger, setOf("002-harbinger-b-s")))
    }

    @Test
    fun cardWithNoArtAnywhereKeepsTheEarliestStandardPrint() {
        assertEquals("001-harbinger-a-s", primarySlug(harbinger, emptySet()))
    }

    @Test
    fun setNamesListEverySetTheCardIsIn() {
        val card = CatalogueBuilder.build(listOf(harbinger), emptySet()).cards.single()
        assertEquals(",Beta,Alpha,", card.setNames)
    }

    @Test
    fun setsAreOrderedByReleaseDate() {
        val sets = CatalogueBuilder.build(listOf(harbinger), emptySet()).sets
        assertEquals(listOf("Alpha" to 1, "Beta" to 2), sets.map { it.name to it.releaseOrder })
    }

    @Test
    fun variantsOfUnnamedSetsAreDropped() {
        // A variant must reference a set row; an unnamed set has none to point at.
        val card = CardJson(
            name = "Ruby Core",
            sets = listOf(set(null, alpha, "x-ruby_core" to "Standard"), set("Alpha", alpha, "001-ruby_core-a-s" to "Standard")),
        )

        val variants = CatalogueBuilder.build(listOf(card), emptySet()).variants

        assertEquals(listOf("001-ruby_core-a-s"), variants.map { it.slug })
    }
}
