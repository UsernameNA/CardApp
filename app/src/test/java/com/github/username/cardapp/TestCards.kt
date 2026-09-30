package com.github.username.cardapp

import com.github.username.cardapp.data.local.CardEntity

fun testCard(
    name: String,
    cardType: String = "Minion",
    rarity: String = "Ordinary",
    cost: Int = 0,
    fireThreshold: Int = 0,
    waterThreshold: Int = 0,
    setNames: String = "",
    rulesText: String = "",
) = CardEntity(
    name = name,
    primarySlug = "001-" + name.lowercase().replace(" ", "_") + "-b-s",
    elements = "",
    subTypes = "",
    cardType = cardType,
    rarity = rarity,
    cost = cost,
    attack = 0,
    defence = 0,
    life = null,
    rulesText = rulesText,
    airThreshold = 0,
    earthThreshold = 0,
    fireThreshold = fireThreshold,
    waterThreshold = waterThreshold,
    setNames = setNames,
)
