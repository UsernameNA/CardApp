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
