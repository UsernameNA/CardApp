package com.github.username.cardapp.data

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for matching: lowercase, diacritics stripped, curly quotes made straight.
 * SQLite's LIKE and NOCASE only fold ASCII, so card text is stored pre-folded (see
 * [com.github.username.cardapp.data.local.CardEntity.searchKey]) and user input is
 * folded the same way before it is compared.
 */
object TextFold {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val curlySingleQuotes = Regex("[\\u2018\\u2019\\u201B\\u2032\\u02BC]")
    private val curlyDoubleQuotes = Regex("[\\u201C\\u201D\\u201F\\u2033]")

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(curlySingleQuotes, "'")
            .replace(curlyDoubleQuotes, "\"")
            .lowercase(Locale.ROOT)

    /** Newline-separated so a search can't match across the end of one field into the next. */
    fun searchKey(name: String, cardType: String, subTypes: String, rulesText: String): String =
        listOf(name, cardType, subTypes, rulesText).joinToString("\n") { fold(it) }
}
