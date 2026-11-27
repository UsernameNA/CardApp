package com.github.username.cardapp.ui.common

/** "1 card", "2 cards": [count] followed by the matching English noun form. */
fun countOf(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
