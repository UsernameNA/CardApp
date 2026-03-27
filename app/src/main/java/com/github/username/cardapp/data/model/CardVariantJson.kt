package com.github.username.cardapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CardVariantJson(
    val slug: String,
    val finish: String? = null,
    val product: String? = null,
    val artist: String? = null,
    val flavorText: String? = null,
    val typeText: String? = null,
)
