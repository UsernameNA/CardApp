package com.github.username.cardapp.data

data class PriceInfo(
    val marketPrice: Double,
    val lowestPrice: Double,
    val setName: String,
    val isPromo: Boolean = false,
)
