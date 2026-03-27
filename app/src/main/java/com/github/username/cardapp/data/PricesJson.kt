package com.github.username.cardapp.data

import kotlinx.serialization.Serializable

@Serializable
data class PricesJson(
    val fetchedAt: String? = null,
    val totalCards: Int? = null,
    val cards: List<PriceEntryJson> = emptyList(),
)
