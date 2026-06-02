package com.github.username.cardapp

import com.github.username.cardapp.ui.scan.FrameFingerprintAnalyzer.fingerprintDiff
import com.github.username.cardapp.ui.scan.FrameFingerprintAnalyzer.fingerprintVariance
import com.github.username.cardapp.ui.scan.FrameFingerprintAnalyzer.hasChanged
import com.github.username.cardapp.ui.scan.FrameFingerprintAnalyzer.hasContent
import com.github.username.cardapp.ui.scan.FrameFingerprintAnalyzer.isStable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintDiffTest {

    @Test
    fun identicalFingerprints() {
        val fp = IntArray(16) { it * 10 }
        assertEquals(0.0, fingerprintDiff(fp, fp), 0.001)
    }

    @Test
    fun completelyDifferent() {
        val a = IntArray(4) { 0 }
        val b = IntArray(4) { 255 }
        assertEquals(255.0, fingerprintDiff(a, b), 0.001)
    }

    @Test
    fun smallDifference() {
        val a = IntArray(4) { 100 }
        val b = intArrayOf(101, 99, 102, 98)
        // diffs: 1 + 1 + 2 + 2 = 6, mean = 1.5
        assertEquals(1.5, fingerprintDiff(a, b), 0.001)
    }
}

class FingerprintVarianceTest {

    @Test
    fun uniformArrayHasZeroVariance() {
        val fp = IntArray(16) { 128 }
        assertEquals(0.0, fingerprintVariance(fp), 0.001)
    }

    @Test
    fun highContrastHasHighVariance() {
        // Half 0, half 255 — high variance
        val fp = IntArray(16) { if (it < 8) 0 else 255 }
        assertTrue("Variance should be high", fingerprintVariance(fp) > 1000)
    }

    @Test
    fun lowContrastHasLowVariance() {
        // All values close to 128
        val fp = IntArray(16) { 127 + (it % 3) }
        assertTrue("Variance should be low", fingerprintVariance(fp) < 5)
    }
}

class FingerprintPredicateTest {

    @Test
    fun stableWhenDiffBelowThreshold() {
        val a = IntArray(4) { 100 }
        val b = IntArray(4) { 105 } // mean diff 5, < STABLE_THRESHOLD (8)
        assertTrue(isStable(a, b))
    }

    @Test
    fun notStableWhenDiffAtOrAboveThreshold() {
        val a = IntArray(4) { 100 }
        val b = IntArray(4) { 110 } // mean diff 10, >= STABLE_THRESHOLD (8)
        assertFalse(isStable(a, b))
    }

    @Test
    fun changedWhenDiffAboveThreshold() {
        val a = IntArray(4) { 100 }
        val b = IntArray(4) { 130 } // mean diff 30, > CHANGE_THRESHOLD (25)
        assertTrue(hasChanged(a, b))
    }

    @Test
    fun notChangedForSmallDiff() {
        val a = IntArray(4) { 100 }
        val b = IntArray(4) { 105 } // mean diff 5, < CHANGE_THRESHOLD (25)
        assertFalse(hasChanged(a, b))
    }

    @Test
    fun hasContentForHighVariance() {
        val fp = IntArray(16) { if (it < 8) 0 else 255 } // variance far above 200
        assertTrue(hasContent(fp))
    }

    @Test
    fun noContentForUniformFrame() {
        val fp = IntArray(16) { 128 } // variance 0
        assertFalse(hasContent(fp))
    }
}
