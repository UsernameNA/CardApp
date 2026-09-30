package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.SetEntity

/** The card catalogue as rows ready for the database. */
data class Catalogue(
    val sets: List<SetEntity>,
    val cards: List<CardEntity>,
    val variants: List<CardVariantEntity>,
)
