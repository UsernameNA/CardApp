package com.github.username.cardapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GuardianStats(
    val rarity: String? = null,
    val type: String? = null,
    val rulesText: String? = null,
    val cost: Int? = null,
    val attack: Int? = null,
    val defence: Int? = null,
    val life: Int? = null,
    val thresholds: Thresholds? = null,
)
