package com.github.username.cardapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CardJson(
    val name: String,
    val guardian: GuardianStats? = null,
    val elements: String? = null,
    val subTypes: String? = null,
    val sets: List<CardSetJson> = emptyList(),
)

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

@Serializable
data class Thresholds(
    val air: Int = 0,
    val earth: Int = 0,
    val fire: Int = 0,
    val water: Int = 0,
)

@Serializable
data class CardSetJson(
    val name: String? = null,
    val releasedAt: String? = null,
    val variants: List<CardVariantJson> = emptyList(),
)

@Serializable
data class CardVariantJson(
    val slug: String,
    val finish: String? = null,
    val product: String? = null,
    val artist: String? = null,
    val flavorText: String? = null,
    val typeText: String? = null,
)
