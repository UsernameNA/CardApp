package com.example.cardapp.data.remote.model

data class ApiCard(
    val name: String,
    val guardian: GuardianStats?,
    val elements: String?,
    val subTypes: String?,
    val sets: List<ApiCardSet> = emptyList(),
)

data class GuardianStats(
    val rarity: String?,
    val type: String?,
    val rulesText: String?,
    val cost: Int?,
    val attack: Int?,
    val defence: Int?,
    val thresholds: Thresholds?,
)

data class Thresholds(
    val air: Int = 0,
    val earth: Int = 0,
    val fire: Int = 0,
    val water: Int = 0,
)

data class ApiCardSet(
    val name: String?,
    val releasedAt: String?,
    val variants: List<ApiVariant> = emptyList(),
)

data class ApiVariant(
    val slug: String,
    val finish: String?,
    val product: String?,
    val artist: String?,
    val flavorText: String?,
    val typeText: String?,
)
