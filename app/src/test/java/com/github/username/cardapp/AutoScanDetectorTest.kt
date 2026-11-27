package com.github.username.cardapp

import com.github.username.cardapp.ui.scan.AutoScanAction
import com.github.username.cardapp.ui.scan.AutoScanDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScanDetectorTest {

    /** High-contrast scene: a card under the camera. */
    private val card = IntArray(256) { if (it % 2 == 0) 20 else 230 }

    /** A different card: the same contrast, inverted, so it differs from [card] by far. */
    private val otherCard = IntArray(256) { if (it % 2 == 0) 230 else 20 }

    /** Featureless scene: an empty table. */
    private val empty = IntArray(256) { 128 }

    private fun detector() = AutoScanDetector(stableFramesRequired = 3)

    /** Feed [frame] [times] times, returning the actions in order. */
    private fun AutoScanDetector.feed(frame: IntArray, times: Int) = List(times) { onFrame(frame) }

    /** Settle [frame] until the detector starts a burst. */
    private fun AutoScanDetector.startBurstOn(frame: IntArray) {
        val actions = feed(frame, 4)
        assertEquals(AutoScanAction.StartBurst, actions.last())
    }

    @Test
    fun startsABurstOnceACardHasBeenStillForEnoughFrames() {
        val actions = detector().feed(card, 4)

        // The first frame has nothing to compare against; the next three are stable.
        assertEquals(
            listOf(AutoScanAction.Skip, AutoScanAction.Skip, AutoScanAction.Skip, AutoScanAction.StartBurst),
            actions,
        )
    }

    @Test
    fun movementRestartsTheStabilityCount() {
        val d = detector()
        d.feed(card, 3)
        d.onFrame(otherCard)

        assertEquals(listOf(AutoScanAction.Skip, AutoScanAction.Skip), d.feed(otherCard, 2))
        assertEquals(AutoScanAction.StartBurst, d.onFrame(otherCard))
    }

    @Test
    fun neverStartsOnAnEmptyScene() {
        assertTrue(detector().feed(empty, 20).all { it == AutoScanAction.Skip })
    }

    @Test
    fun framesFeedTheBurstWhileItIsInFlight() {
        val d = detector()
        d.startBurstOn(card)

        assertEquals(listOf(AutoScanAction.FeedBurst, AutoScanAction.FeedBurst), d.feed(card, 2))
    }

    @Test
    fun afterAMatchWaitsForTheCardToBeTakenAway() {
        val d = detector()
        d.startBurstOn(card)
        d.onBurstFinished(matched = true)

        assertTrue(d.isWaitingForChange)
        assertTrue("the same card must not be scanned again", d.feed(card, 20).all { it == AutoScanAction.Skip })

        assertEquals(AutoScanAction.Rearm, d.onFrame(otherCard))
        assertFalse(d.isWaitingForChange)
    }

    @Test
    fun afterRearmingTheNextCardMustSettleBeforeItIsRead() {
        val d = detector()
        d.startBurstOn(card)
        d.onBurstFinished(matched = true)
        d.onFrame(otherCard)

        assertEquals(listOf(AutoScanAction.Skip, AutoScanAction.Skip), d.feed(otherCard, 2))
        assertEquals(AutoScanAction.StartBurst, d.onFrame(otherCard))
    }

    @Test
    fun aCardThatFailedToMatchIsNotRetriedUntilTheSceneChanges() {
        val d = detector()
        d.startBurstOn(card)
        d.onBurstFinished(matched = false)

        assertFalse(d.isWaitingForChange)
        assertTrue("retrying the same scene would loop forever", d.feed(card, 20).all { it == AutoScanAction.Skip })

        // A different card is a new scene and gets its own attempt.
        d.onFrame(otherCard)
        d.feed(otherCard, 2)
        assertEquals(AutoScanAction.StartBurst, d.onFrame(otherCard))
    }

    @Test
    fun anAbandonedBurstGoesBackToWatchingTheSameScene() {
        // A timed-out or cancelled burst reached no verdict, so the card deserves a retry.
        val d = detector()
        d.startBurstOn(card)
        d.onBurstAbandoned()

        assertEquals(listOf(AutoScanAction.Skip, AutoScanAction.Skip), d.feed(card, 2))
        assertEquals(AutoScanAction.StartBurst, d.onFrame(card))
    }

    @Test
    fun aVerdictWithNoBurstInFlightIsIgnored() {
        val d = detector()
        d.feed(card, 2)

        d.onBurstFinished(matched = true)

        assertFalse("a stale verdict must not wedge auto mode", d.isWaitingForChange)
        assertEquals(AutoScanAction.StartBurst, d.feed(card, 2).last())
    }

    @Test
    fun resetForgetsTheLastScan() {
        val d = detector()
        d.startBurstOn(card)
        d.onBurstFinished(matched = true)

        d.reset()

        assertFalse(d.isWaitingForChange)
        d.startBurstOn(card)
    }
}
