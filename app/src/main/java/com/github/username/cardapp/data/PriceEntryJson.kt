package com.github.username.cardapp.data

import kotlinx.serialization.Serializable

@Serializable
data class PriceEntryJson(
    val productName: String? = null,
    val setName: String? = null,
    val marketPrice: Double? = null,
    val lowestPrice: Double? = null,
)
