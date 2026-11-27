package com.github.username.cardapp.ui.scan

/** What [AutoScanDetector] wants done with the camera frame it was just shown. */
enum class AutoScanAction {
    /** Nothing to do; release the frame. */
    Skip,

    /** A card has settled: open a burst and make this frame its first read. */
    StartBurst,

    /** A burst is reading: offer this frame to it. */
    FeedBurst,

    /** The scanned card was taken away; watch for the next one. Release the frame. */
    Rearm,
}
