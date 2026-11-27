package com.github.username.cardapp.ui.scan

/**
 * Auto-mode card detection: decides, frame by frame, when a card has settled under the
 * camera and should be read, and when it has been taken away so the next can be.
 *
 * Each camera frame is reduced to a fingerprint ([FrameFingerprintAnalyzer]) that drives a
 * three-state machine:
 *
 *   WaitingForCard   — count consecutive stable frames; once the scene is still and has
 *                      content (and isn't the scene that just failed to match), start a burst
 *   OcrInFlight      — a burst is reading; frames feed it
 *   WaitingForChange — a card was just matched; wait until the scene diverges from it
 *                      (card removed / swapped) before watching again
 *
 * Frames arrive on the camera analysis thread while burst verdicts and resets come from
 * the main thread, so all state is guarded.
 */
class AutoScanDetector(
    private val stableFramesRequired: Int = FrameFingerprintAnalyzer.STABLE_FRAMES_REQUIRED,
) {

    private enum class State { WaitingForCard, OcrInFlight, WaitingForChange }

    private val lock = Any()
    private var state = State.WaitingForCard
    private var prevFingerprint: IntArray? = null
    private var stableFrameCount = 0

    /** The frame that started the burst in flight. */
    private var burstFingerprint: IntArray? = null

    /** The scene of the last match; the next card is watched for once the frame leaves it. */
    private var scannedFingerprint: IntArray? = null

    /** The scene of the last failed burst, so a card that can't be read isn't retried forever. */
    private var failedFingerprint: IntArray? = null

    /** True between a match and the card being taken away. */
    val isWaitingForChange: Boolean get() = synchronized(lock) { state == State.WaitingForChange }

    /** Forget everything and start watching for a card. */
    fun reset() = synchronized(lock) {
        state = State.WaitingForCard
        prevFingerprint = null
        stableFrameCount = 0
        burstFingerprint = null
        scannedFingerprint = null
        failedFingerprint = null
    }

    fun onFrame(fingerprint: IntArray): AutoScanAction = synchronized(lock) {
        when (state) {
            State.WaitingForCard -> {
                val prev = prevFingerprint
                if (prev != null) {
                    if (FrameFingerprintAnalyzer.isStable(prev, fingerprint)) {
                        stableFrameCount++
                    } else {
                        stableFrameCount = 0
                        failedFingerprint = null // scene changed, allow retry
                    }
                }
                prevFingerprint = fingerprint

                val notPreviouslyFailed = failedFingerprint
                    ?.let { FrameFingerprintAnalyzer.hasChanged(it, fingerprint) } ?: true
                if (stableFrameCount >= stableFramesRequired &&
                    FrameFingerprintAnalyzer.hasContent(fingerprint) &&
                    notPreviouslyFailed
                ) {
                    stableFrameCount = 0
                    burstFingerprint = fingerprint
                    state = State.OcrInFlight
                    AutoScanAction.StartBurst
                } else {
                    AutoScanAction.Skip
                }
            }

            State.OcrInFlight -> {
                prevFingerprint = fingerprint
                AutoScanAction.FeedBurst
            }

            State.WaitingForChange -> {
                prevFingerprint = fingerprint
                // With no reference to compare against, treat the scene as changed rather
                // than waiting forever.
                val scanned = scannedFingerprint
                if (scanned == null || FrameFingerprintAnalyzer.hasChanged(scanned, fingerprint)) {
                    state = State.WaitingForCard
                    stableFrameCount = 0
                    failedFingerprint = null
                    AutoScanAction.Rearm
                } else {
                    AutoScanAction.Skip
                }
            }
        }
    }

    /** The burst started by [onFrame] reached a verdict. Ignored when no burst is in flight. */
    fun onBurstFinished(matched: Boolean) = synchronized(lock) {
        if (state != State.OcrInFlight) return@synchronized
        if (matched) {
            scannedFingerprint = burstFingerprint
            state = State.WaitingForChange
        } else {
            failedFingerprint = burstFingerprint
            state = State.WaitingForCard
        }
        burstFingerprint = null
    }

    /** The burst in flight was dropped without a verdict; go back to watching. */
    fun onBurstAbandoned() = synchronized(lock) {
        if (state != State.OcrInFlight) return@synchronized
        state = State.WaitingForCard
        stableFrameCount = 0
        burstFingerprint = null
    }
}
