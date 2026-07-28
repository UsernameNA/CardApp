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

    @Test
    fun apostropheInNameSurvivesTypographicQuote() {
        // ML Kit emits a curly apostrophe where the catalogue stores a straight one.
        // Both must strip to the same text or the full-name match is lost.
        val cards = listOf(card("Sorcerer's Tower", cardType = "Site"), card("Goblin"))
        val ocr = "Sorcerer’s Tower\nSite\nTap for mana."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Sorcerer's Tower", result.matched?.name)
        assertTrue(
            "curly apostrophe should still yield the full-name score",
            result.bestScore >= 200,
        )
    }

    // ── Margin gate ─────────────────────────────────────────────────────────────

    @Test
    fun rejectsMatchWhenRunnerUpIsEquallyPlausible() {
        // "Serpant" is one substitution away from both catalogue names, so neither can be
        // preferred. Clearing the threshold is not enough on its own — without the margin
        // gate this would silently add a coin-flip guess to the collection.
        val cards = listOf(card("Sea Serpent"), card("Sea Servant"))
        val ocr = "Sea Serpant\nMinion\nA creature of the deep."

        val result = OcrCardScorer.score(ocr, cards)

        assertTrue(
            "both should clear the raw threshold",
            result.bestScore >= OcrCardScorer.SCORE_THRESHOLD,
        )
        assertEquals(
            "the two candidates should be tied",
            result.top3[0].score,
            result.top3[1].score,
        )
        assertNull("a tie must not auto-match", result.matched)
        assertEquals("candidates are still surfaced for correction", 2, result.top3.size)
    }

    @Test
    fun acceptsMatchWithAClearLead() {
        val cards = listOf(card("Sea Serpent"), card("Goblin"), card("Flame Dragon"))
        val ocr = "Sea Serpent\nMinion - Beast\nA serpent of the deep."

        val result = OcrCardScorer.score(ocr, cards)

        assertEquals("Sea Serpent", result.matched?.name)
    }

    // ── Multi-frame bursts ──────────────────────────────────────────────────────

    @Test
    fun combineAveragesSoThresholdKeepsItsMeaning() {
        val cards = listOf(card("Sea Serpent"), card("Goblin"))
        val ocr = "Sea Serpent\nMinion - Beast\nA serpent of the deep."

        val single = OcrCardScorer.score(ocr, cards)
        val burst = OcrCardScorer.combine(
            List(3) { OcrCardScorer.scoreFrame(ocr, cards) },
            cards,
        )

        assertEquals(
            "three identical reads must not inflate the score",
            single.bestScore,
            burst.bestScore,
        )
        assertEquals("Sea Serpent", burst.matched?.name)
    }

    @Test
    fun burstRecoversACardThatOneBadFrameWouldMiss() {
        // Frame 2 is badly garbled and on its own picks the wrong card; the two good
        // reads outvote it.
        val cards = listOf(card("Sea Serpent"), card("Flame Dragon"))
        val good = "Sea Serpent\nMinion - Beast\nA serpent of the deep."
        val garbled = "Flarne Dragon"

        assertEquals(
            "the bad frame alone should mismatch",
            "Flame Dragon",
            OcrCardScorer.score(garbled, cards).best?.name,
        )

        val burst = OcrCardScorer.combine(
            listOf(
                OcrCardScorer.scoreFrame(good, cards),
                OcrCardScorer.scoreFrame(garbled, cards),
                OcrCardScorer.scoreFrame(good, cards),
            ),
            cards,
        )

        assertEquals("Sea Serpent", burst.matched?.name)
    }

    @Test
    fun combineIgnoresFailedFrames() {
        val cards = listOf(card("Sea Serpent"), card("Goblin"))
        val ocr = "Sea Serpent\nMinion - Beast\nA serpent of the deep."

        // A frame whose OCR failed contributes no scores and must not drag the average down.
        val withFailure = OcrCardScorer.combine(
            listOf(
                OcrCardScorer.scoreFrame(ocr, cards),
                OcrCardScorer.FrameScores("", emptyMap(), emptySet()),
            ),
            cards,
        )

        assertEquals(OcrCardScorer.score(ocr, cards).bestScore, withFailure.bestScore)
        assertEquals("Sea Serpent", withFailure.matched?.name)
    }

    @Test
    fun combineWithNoUsableFramesYieldsNoMatch() {
        val cards = listOf(card("Sea Serpent"))

        val result = OcrCardScorer.combine(emptyList(), cards)

        assertNull(result.matched)
        assertNull(result.best)
    }
}
