package com.github.username.cardapp.ui.common

import java.util.Locale

/** US-dollar price strings; fixed to [Locale.US] so the decimal separator matches the "$". */
object PriceFormat {

    /** Dollars and cents: "$1.50". */
    fun full(price: Double): String = String.format(Locale.US, "$%.2f", price)

    /** Short form for narrow columns: "$1234", "$1.50", "50¢". */
    fun compact(price: Double): String {
        val cents = Math.round(price * 100)
        return when {
            price >= 1000 -> "$${price.toInt()}"
            cents >= 100 -> full(cents / 100.0)
            else -> "$cents¢"
        }
    }
}
