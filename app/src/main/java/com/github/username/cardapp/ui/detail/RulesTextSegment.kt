package com.github.username.cardapp.ui.detail

sealed interface RulesTextSegment {
    data class Text(val text: String) : RulesTextSegment

    /** An element symbol; [element] is the lower-case name ("air", "earth", "fire", "water"). */
    data class Element(val element: String) : RulesTextSegment
}
