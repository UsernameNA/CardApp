package com.github.username.cardapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Thresholds(
    val air: Int = 0,
    val earth: Int = 0,
    val fire: Int = 0,
    val water: Int = 0,
)
