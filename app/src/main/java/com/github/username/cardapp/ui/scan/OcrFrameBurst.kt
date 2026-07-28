package com.github.username.cardapp.ui.scan

/**
 * Collects the per-frame results of a multi-frame OCR burst.
 *
 * A scan reads the same card [SIZE] times and merges the scores. Character misreads are
 * largely uncorrelated between frames while the true card scores well in every one, so a
 * burst recovers matches that any single read would get wrong.
 *
 * Reads finish on arbitrary ML Kit callback threads and in arbitrary order, so all state
 * is guarded. A [generation] token lets an in-flight burst be abandoned (new scan tap,
 * scan-mode toggle) without its late-arriving frames corrupting the next one.
 */
class OcrFrameBurst(private val size: Int = SIZE) {

    private val lock = Any()
    private val frames = mutableListOf<OcrCardScorer.FrameScores>()
    private var completed = 0
    private var generation = 0

    /** Abandon any burst in flight and open a new one. Returns the new generation token. */
    fun restart(): Int = synchronized(lock) {
        frames.clear()
        completed = 0
        ++generation
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
            if (generation != this.generation) return null
            if (scores != null) frames.add(scores)
            if (++completed < size) null else frames.toList()
        }

    companion object {
        /** Frames per scan. Three is where the accuracy curve flattens. */
        const val SIZE = 3
    }
}
