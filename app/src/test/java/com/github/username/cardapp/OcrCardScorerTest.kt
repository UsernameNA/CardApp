package com.github.username.cardapp

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.scan.OcrCardScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCardScorerTest {

    private fun card(
        name: String,
        cardType: String = "Minion",
        rulesText: String = "",
        subTypes: String = "",
        cost: Int = 0,
    ) = CardEntity(
        name = name,
        primarySlug = name.lowercase().replace(" ", "_"),
        elements = "",
        subTypes = subTypes,
        cardType = cardType,
        rarity = "Ordinary",
        cost = cost,
        attack = 0,
        defence = 0,
        life = null,
        rulesText = rulesText,
        airThreshold = 0,
        earthThreshold = 0,
        fireThreshold = 0,
        waterThreshold = 0,
    )

    @Test
    fun matchesExactNameInNameZone() {
        val cards = listOf(card("Sea Serpent"), card("Goblin"), card("Flame Dragon"))
        val ocr = "Sea Serpent\nMinion - Beast\nThis is a big snake from the sea."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Sea Serpent", result.matched?.name)
        assertEquals("Sea Serpent", result.best?.name)
        assertTrue("score should clear threshold", result.bestScore >= OcrCardScorer.SCORE_THRESHOLD)
    }

    @Test
    fun returnsNoMatchWhenNothingScoresAboveThreshold() {
        val cards = listOf(card("Sea Serpent"), card("Goblin"))
        val ocr = "zzzz xxxx wwww qqqq"

        val result = OcrCardScorer.score(ocr, cards)

        assertNull("garbage OCR should not match", result.matched)
    }

    @Test
    fun nameZoneMatchBeatsBodyKeywordDecoy() {
        // The real card's name is in the name zone; the decoy merely mentions
        // "serpent" in its rules text. The name-zone match must win.
        val real = card("Sea Serpent")
        val decoy = card("Goblin Shaman", rulesText = "Summon a serpent token each turn.")
        val ocr = "Sea Serpent\nMinion - Beast\nA serpent of the deep."

        val result = OcrCardScorer.score(ocr, listOf(decoy, real))

        assertEquals("Sea Serpent", result.matched?.name)
    }

    @Test
    fun normalizesCommonOcrMisreadInName() {
        // "Amber" misread as "Arnber" (the classic rn→m OCR error) is normalized
        // back to an exact name match.
        val cards = listOf(card("Amber"), card("Goblin"))
        val ocr = "Arnber\nMinion\nBurns the enemy."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Amber", result.matched?.name)
    }

    @Test
    fun correctlyReadNameContainingRnStillMatches() {
        // A single-word name that genuinely contains "rn", read correctly by OCR,
        // must still match — normalization must not corrupt only one side of the
        // comparison ("Burn" OCR text becomes "Bum" while the card name stays "Burn").
        val cards = listOf(card("Burn", cardType = "Magic"), card("Goblin"))
        val ocr = "Burn\nMagic\nDeal damage to a target."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Burn", result.matched?.name)
    }

    @Test
    fun correctlyReadNameContainingClStillMatches() {
        // Same one-sided corruption for "cl" → "d": OCR "Clock" becomes "Dock".
        val cards = listOf(card("Clock", cardType = "Artifact"), card("Goblin"))
        val ocr = "Clock\nArtifact\nAt the start of each turn tick once."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Clock", result.matched?.name)
    }

    @Test
    fun singleFuzzyNameMatchAloneStaysBelowThreshold() {
        // A one-word name with a single OCR substitution ("Em8er" for "Ember")
        // scores below threshold on its own — by design, to avoid false positives
        // without corroborating signal (cost / rules / type).
        val cards = listOf(card("Ember"))
        val ocr = "Em8er"

        val result = OcrCardScorer.score(ocr, cards)

        assertNull(result.matched)
        assertTrue("best should still surface as a candidate", result.bestScore < OcrCardScorer.SCORE_THRESHOLD)
    }

    @Test
    fun emptyCardListYieldsNoMatch() {
        val result = OcrCardScorer.score("Sea Serpent", emptyList())

        assertNull(result.matched)
        assertNull(result.best)
    }
}
