package com.github.username.cardapp.ui.scan

import kotlinx.serialization.Serializable

@Serializable
data class ScanCandidate(
    val name: String,
    val score: Int,
)

@Serializable
data class ScanDebugInfo(
    val rawText: String,
    val costCandidates: Set<Int>,
    val bestCardName: String?,
    val bestScore: Int,
    val top3: List<ScanCandidate> = emptyList(),
)
