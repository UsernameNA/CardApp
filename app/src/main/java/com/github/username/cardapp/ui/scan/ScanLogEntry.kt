package com.github.username.cardapp.ui.scan

import kotlinx.serialization.Serializable

@Serializable
data class ScanLogEntry(
    val rawText: String,
    val costCandidates: Set<Int>,
    val bestMatch: String?,
    val bestScore: Int,
    val top3: List<ScanCandidate>,
    val actualCard: String,
)
