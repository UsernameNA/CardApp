package com.github.username.cardapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CardSetJson(
    val name: String? = null,
    val releasedAt: String? = null,
    val variants: List<CardVariantJson> = emptyList(),
)
