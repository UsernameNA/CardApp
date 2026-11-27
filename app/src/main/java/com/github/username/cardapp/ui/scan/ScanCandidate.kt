package com.github.username.cardapp.ui.scan

import kotlinx.serialization.Serializable

@Serializable
data class ScanCandidate(
    val name: String,
    val score: Int,
)
