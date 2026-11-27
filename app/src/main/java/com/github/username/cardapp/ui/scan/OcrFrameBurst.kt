package com.github.username.cardapp.ui.scan

/**
 * Collects the per-frame results of a multi-frame OCR burst.
 *
 * A scan reads the same card [size] times and merges the scores, so a misread in one
 * read is outvoted by the others. Adjacent camera frames are near-duplicates whose
 * misreads mostly coincide, so reads are spaced at least [minFrameIntervalNanos] apart:
 * small shifts in the hand, focus and exposure make each read a different sample.
 *
 * Frames are claimed on the camera analysis thread and reads finish on arbitrary ML Kit
 * callback threads in arbitrary order, so all state is guarded. A generation token lets
 * an in-flight burst be abandoned (new scan tap, scan-mode toggle) without its
 * late-arriving frames corrupting the next one.
 */
class OcrFrameBurst(
    private val size: Int = SIZE,
    private val minFrameIntervalNanos: Long = MIN_FRAME_INTERVAL_NANOS,
) {

    private val lock = Any()
    private val frames = mutableListOf<OcrCardScorer.FrameScores>()
    private var completed = 0
    private var generation = 0
    private var unclaimed = 0
    private var lastClaimNanos: Long? = null
    private var pending = false

    /** Abandon any burst in flight and open a new one. Returns the new generation token. */
    fun restart(): Int = synchronized(lock) {
        reset()
        unclaimed = size
        pending = true
        generation
    }

    /** Abandon any burst in flight without opening another. */
    fun cancel() {
        synchronized(lock) { reset() }
    }

    /**
     * Abandon burst [generation] if it is still waiting for reads.
     *
     * @return true if it was, false if it already completed or was superseded.
     */
    fun cancelIfPending(generation: Int): Boolean = synchronized(lock) {
        if (generation != this.generation || !pending) return false
        reset()
        true
    }

    /** True while [generation] is the latest burst and has not been cancelled. */
    fun isCurrent(generation: Int): Boolean = synchronized(lock) { generation == this.generation }

    private fun reset() {
        frames.clear()
        completed = 0
        unclaimed = 0
        lastClaimNanos = null
        pending = false
        ++generation
    }

    /**
     * Offer the camera frame captured at [timestampNanos] as the burst's next read.
     *
     * @return the generation the read belongs to, or null when the frame should be
     *   skipped — no burst wants reads, or it is too soon after the previous read.
     */
    fun claim(timestampNanos: Long): Int? = synchronized(lock) {
        if (unclaimed == 0) return null
        val last = lastClaimNanos
        if (last != null && timestampNanos - last < minFrameIntervalNanos) return null
        lastClaimNanos = timestampNanos
        unclaimed--
        generation
    }

    /**
     * Record one finished read — [scores] is null when the frame could not be captured or
     * OCR failed. Failed frames still count toward completion so a dropped frame cannot
     * stall the burst forever.
     *
     * @return the collected frames once the burst is complete, otherwise null. Returns null
     *   for reads belonging to an abandoned generation.
     */
    fun report(generation: Int, scores: OcrCardScorer.FrameScores?): List<OcrCardScorer.FrameScores>? =
        synchronized(lock) {
            if (generation != this.generation || !pending) return null
            if (scores != null) frames.add(scores)
            if (++completed < size) return null
            pending = false
            frames.toList()
        }

    companion object {
        /** Frames per scan. Three is where the accuracy curve flattens. */
        const val SIZE = 3

        /** Minimum spacing between reads: about five frames at 30fps. */
        const val MIN_FRAME_INTERVAL_NANOS = 150_000_000L
    }
}
