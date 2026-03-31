package com.github.username.cardapp.data

import kotlinx.serialization.Serializable

@Serializable
data class FaqEntry(val question: String, val answer: String)
