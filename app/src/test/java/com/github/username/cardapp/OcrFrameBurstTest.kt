package com.github.username.cardapp

import com.github.username.cardapp.ui.scan.OcrCardScorer
import com.github.username.cardapp.ui.scan.OcrFrameBurst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrFrameBurstTest {

    private fun frame(text: String) =
        OcrCardScorer.FrameScores(text, mapOf("Sea Serpent" to 200), emptySet())

    @Test
    fun completesOnlyAfterEveryReadReports() {
        val burst = OcrFrameBurst(size = 3)
        val gen = burst.restart()

        assertNull(burst.report(gen, frame("a")))
        assertNull(burst.report(gen, frame("b")))

        val done = burst.report(gen, frame("c"))
        assertEquals(3, done?.size)
    }

    @Test
    fun failedReadsStillCountTowardCompletion() {
        // Otherwise a dropped frame would stall the burst forever and wedge auto mode.
        val burst = OcrFrameBurst(size = 3)
        val gen = burst.restart()

        burst.report(gen, frame("a"))
        burst.report(gen, null)
        val done = burst.report(gen, frame("c"))

        assertEquals("failed frame is counted but contributes nothing", 2, done?.size)
    }

    @Test
    fun allFramesFailingStillCompletesTheBurst() {
        val burst = OcrFrameBurst(size = 2)
        val gen = burst.restart()

        assertNull(burst.report(gen, null))
        val done = burst.report(gen, null)

        assertEquals(emptyList<OcrCardScorer.FrameScores>(), done)
    }

    @Test
    fun staleReadsFromAnAbandonedBurstAreDiscarded() {
        val burst = OcrFrameBurst(size = 2)
        val old = burst.restart()
        burst.report(old, frame("stale"))

        val new = burst.restart()

        // A late frame from the abandoned burst must neither complete nor pollute the new one.
        assertNull(burst.report(old, frame("stale-2")))
        assertNull(burst.report(new, frame("fresh-1")))

        val done = burst.report(new, frame("fresh-2"))
        assertEquals(listOf("fresh-1", "fresh-2"), done?.map { it.rawText })
    }

    @Test
    fun restartClearsFramesFromThePreviousBurst() {
        val burst = OcrFrameBurst(size = 2)
        val first = burst.restart()
        burst.report(first, frame("a"))

        val second = burst.restart()
        burst.report(second, frame("x"))
        val done = burst.report(second, frame("y"))

        assertEquals(listOf("x", "y"), done?.map { it.rawText })
    }

    // ── Frame claiming ──────────────────────────────────────────────────────────

    private val ms = 1_000_000L

    @Test
    fun claimsAreSpacedAtLeastTheMinimumIntervalApart() {
        val burst = OcrFrameBurst(size = 3, minFrameIntervalNanos = 150 * ms)
        val gen = burst.restart()

        assertEquals(gen, burst.claim(0))
        assertNull("adjacent frames are near-duplicates", burst.claim(33 * ms))
        assertNull(burst.claim(149 * ms))
        assertEquals(gen, burst.claim(150 * ms))
        assertEquals(gen, burst.claim(300 * ms))
    }

    @Test
    fun noFramesAreClaimedOnceTheBurstHasAllItsReads() {
        val burst = OcrFrameBurst(size = 2, minFrameIntervalNanos = 0)
        burst.restart()

        burst.claim(0)
        burst.claim(1)

        assertNull(burst.claim(2))
    }

    @Test
    fun nothingIsClaimedBeforeABurstStarts() {
        assertNull(OcrFrameBurst().claim(0))
    }

    @Test
    fun cancelStopsClaimsAndDiscardsLateReads() {
        val burst = OcrFrameBurst(size = 2, minFrameIntervalNanos = 0)
        val gen = burst.restart()
        burst.claim(0)

        burst.cancel()

        assertNull(burst.claim(1))
        assertNull(burst.report(gen, frame("late")))
        assertNull(burst.report(gen, frame("later")))
    }

    @Test
    fun restartResetsTheSpacingClock() {
        val burst = OcrFrameBurst(size = 3, minFrameIntervalNanos = 150 * ms)
        burst.restart()
        burst.claim(100 * ms)

        val gen = burst.restart()

        assertEquals("a new scan reads its first frame immediately", gen, burst.claim(110 * ms))
    }
}
