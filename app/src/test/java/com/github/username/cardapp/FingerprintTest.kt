package com.github.username.cardapp

import com.github.username.cardapp.ui.scan.ScanViewModel.Companion.fingerprintDiff
import com.github.username.cardapp.ui.scan.ScanViewModel.Companion.fingerprintVariance
import org.junit.Assert.assertEquals
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
