package com.github.username.cardapp.ui.scan

data class ScanDebugInfo(
    val rawText: String,
    val costCandidates: Set<Int>,
    val bestCardName: String?,
    val bestScore: Int,
)
