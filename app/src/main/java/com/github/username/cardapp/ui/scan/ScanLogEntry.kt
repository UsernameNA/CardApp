package com.github.username.cardapp.ui.scan

import kotlinx.serialization.Serializable

@Serializable
data class ScanLogEntry(
    /** The [ScanOutcome.id] this scan was shown under. */
    val scanId: Long,
    val rawText: String,
    val costCandidates: Set<Int>,
    val bestMatch: String?,
    val bestScore: Int,
    val top3: List<ScanCandidate>,
    /** What the scan added automatically; null when it wasn't confident enough. */
    val acceptedMatch: String?,
    /** The card the user picked in the correction panel; empty when they never corrected. */
    val actualCard: String = "",
)
