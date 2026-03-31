package com.github.username.cardapp.ui.theme

import androidx.compose.ui.graphics.Color

// Vault palette — near-black ground with a whisper of warmth in the surfaces
val LeatherDeep      = Color(0xFF080705)   // near-pure-black foundation
val LeatherDark      = Color(0xFF0F0C09)   // deep background layer
val LeatherMid       = Color(0xFF1D1810)   // main surface — dark espresso charcoal
val LeatherLight     = Color(0xFF2E2519)   // raised surface — warm dark slate
//val LeatherHighlight = Color(0xFF3E3228)   // specular — gunmetal with warmth

// Gold — antique and aged (pops more against the darker ground)
val GoldLight   = Color(0xFFEDD07A)
val GoldPrimary = Color(0xFFC9A84C)
val GoldMuted   = Color(0xFF8A6C30)
val GoldDark    = Color(0xFF524018)

// Parchment / Cream
val CreamPrimary = Color(0xFFF0E2C0)
val CreamMuted   = Color(0xFFCBB98A)
val CreamFaded   = Color(0xFF7A6848)

// Accents
val BurgundyAccent = Color(0xFF7A2535)
val BurgundyLight  = Color(0xFF9E3045)

// Utility
val InkShadow = Color(0xCC040302)

// Elements
val FireElement  = Color(0xFFCF6842)
val WaterElement = Color(0xFF5B94C4)
val EarthElement = Color(0xFF8B6914)
val AirElement   = Color(0xFFB0C0D0)

fun elementColor(element: String): Color = when (element.lowercase()) {
    "fire" -> FireElement
    "water" -> WaterElement
    "earth" -> EarthElement
    "air" -> AirElement
    else -> GoldMuted
}

fun rarityColor(rarity: String): Color = when (rarity.trim().lowercase()) {
    "ordinary" -> Color(0xFF909090)
    "exceptional" -> Color(0xFF2E642E)
    "elite" -> Color(0xFF4A6EC9)
    "unique" -> BurgundyAccent
    else -> GoldMuted
}
