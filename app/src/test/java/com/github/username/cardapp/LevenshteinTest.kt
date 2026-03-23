package com.github.username.cardapp

import com.github.username.cardapp.ui.scan.ScanViewModel.Companion.levenshtein
import com.github.username.cardapp.ui.scan.ScanViewModel.Companion.normalizeOcr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevenshteinTest {

    // Results are doubled (sub cost = 2 for normal, 1 for OCR-confused pairs)

    @Test
    fun identicalStrings() {
        assertEquals(0, levenshtein("hello", "hello"))
    }

    @Test
    fun emptyStrings() {
        assertEquals(0, levenshtein("", ""))
    }

    @Test
    fun oneEmpty() {
        assertEquals(6, levenshtein("abc", ""))  // 3 deletions * 2
        assertEquals(6, levenshtein("", "abc"))  // 3 insertions * 2
    }

    @Test
    fun singleSubstitution() {
        // 'a' -> 'b' is not a confused pair, costs 2
        assertEquals(2, levenshtein("cat", "cbt"))
    }

    @Test
    fun ocrConfusedPairCostsLess() {
        // '0' and 'o' are an OCR confused pair, sub costs 1 instead of 2
        val confused = levenshtein("0", "o")
        val normal = levenshtein("a", "b")
        assertTrue("Confused pair ($confused) should cost less than normal ($normal)", confused < normal)
        assertEquals(1, confused)
        assertEquals(2, normal)
    }

    @Test
    fun ocrConfusedPairOneAndL() {
        assertEquals(1, levenshtein("1", "l"))
    }

    @Test
    fun ocrConfusedPairFiveAndS() {
        assertEquals(1, levenshtein("5", "s"))
    }

    @Test
    fun insertionCosts() {
        // "ab" -> "abc" = 1 insertion * 2
        assertEquals(2, levenshtein("ab", "abc"))
    }

    @Test
    fun deletionCosts() {
        // "abc" -> "ab" = 1 deletion * 2
        assertEquals(2, levenshtein("abc", "ab"))
    }

    @Test
    fun realCardNameFuzzyMatch() {
        // "abundance" vs "abundonce" — 'a' -> 'o' normal sub
        val dist = levenshtein("abundance", "abundonce")
        assertEquals(2, dist)  // one normal substitution
    }

    @Test
    fun realOcrGarbledName() {
        // "ember" misread as "em8er" — 'b' and '8' are confused
        val dist = levenshtein("ember", "em8er")
        assertEquals(1, dist)  // one confused substitution
    }
}

class NormalizeOcrTest {

    @Test
    fun fixesRnToM() {
        assertEquals("mine", normalizeOcr("rnine"))
    }

    @Test
    fun fixesVvToW() {
        assertEquals("sword", normalizeOcr("svvord"))
    }

    @Test
    fun fixesClToD() {
        assertEquals("dragon", normalizeOcr("clragon"))
    }

    @Test
    fun noChangesForCleanText() {
        assertEquals("hello world", normalizeOcr("hello world"))
    }

    @Test
    fun multipleFixesInOneString() {
        assertEquals("mw", normalizeOcr("rnvv"))
    }
}
